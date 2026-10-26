package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.build.ArtifactRegistration;
import com.snail.platform.common.build.BuildStep;
import com.snail.platform.common.build.InternalRunContext;
import com.snail.platform.common.build.PipelineDefinition;
import com.snail.platform.common.build.RunStatusUpdate;
import com.snail.platform.common.model.RunStatus;
import com.snail.platform.control.entity.BuildArtifactEntity;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.PipelineRunEntity;
import com.snail.platform.control.entity.ProjectEntity;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.mapper.BuildArtifactMapper;
import com.snail.platform.control.mapper.PipelineMapper;
import com.snail.platform.control.mapper.PipelineRunMapper;
import com.snail.platform.control.mapper.ProjectMapper;
import com.snail.platform.control.mapper.RepoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** worker 专用的内部能力：拉取执行上下文、回报状态、登记制品。 */
@Service
@RequiredArgsConstructor
public class InternalService {

    private final PipelineRunMapper runMapper;
    private final PipelineMapper pipelineMapper;
    private final RepoMapper repoMapper;
    private final ProjectMapper projectMapper;
    private final BuildArtifactMapper artifactMapper;
    private final RepoCredentialService repoCredentialService;
    private final ProjectSecretService projectSecretService;
    private final ObjectMapper objectMapper;

    public InternalRunContext context(Long runId) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw BizException.notFound("运行记录不存在: " + runId);
        }
        PipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());
        if (pipeline == null) {
            throw BizException.notFound("流水线不存在: " + run.getPipelineId());
        }
        RepoEntity repo = pipeline.getRepoId() == null ? null : repoMapper.selectById(pipeline.getRepoId());
        ProjectEntity project = projectMapper.selectById(pipeline.getProjectId());
        String definitionJson = StringUtils.hasText(run.getResolvedDefinition())
                ? run.getResolvedDefinition() : pipeline.getDefinitionJson();
        List<BuildStep> steps;
        try {
            PipelineDefinition definition = objectMapper.readValue(definitionJson, PipelineDefinition.class);
            steps = definition.getSteps() == null ? List.of() : definition.getSteps();
        } catch (Exception ex) {
            throw BizException.badRequest("流水线定义无法解析: " + ex.getMessage());
        }
        RepoCredentialService.Credential credential = repo == null ? null
                : repoCredentialService.find(repo.getId());
        return new InternalRunContext(run.getId(), pipeline.getId(),
                project == null ? null : project.getProjectKey(),
                repo == null ? null : repo.getCloneUrl(),
                run.getBranch(), run.getCommitSha(), pipeline.getExecutorType(), steps,
                credential == null ? null : credential.username(),
                credential == null ? null : credential.token(),
                project == null ? Map.of() : projectSecretService.envOf(project.getId()));
    }

    public void updateStatus(Long runId, RunStatusUpdate update) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw BizException.notFound("运行记录不存在: " + runId);
        }
        if (RunStatus.CANCELED.name().equals(run.getStatus())) {
            return;
        }
        RunStatus status = parse(update.status());
        run.setStatus(status.name());
        if (status == RunStatus.RUNNING && run.getStartedAt() == null) {
            run.setStartedAt(LocalDateTime.now());
        }
        if (status == RunStatus.SUCCESS || status == RunStatus.FAILED || status == RunStatus.CANCELED) {
            run.setFinishedAt(LocalDateTime.now());
        }
        if (update.logObject() != null) {
            run.setLogObject(update.logObject());
        }
        if (update.errorMessage() != null) {
            run.setErrorMessage(update.errorMessage().length() > 1000
                    ? update.errorMessage().substring(0, 1000) : update.errorMessage());
        }
        runMapper.updateById(run);
    }

    public void registerArtifact(Long runId, ArtifactRegistration registration) {
        PipelineRunEntity run = runMapper.selectById(runId);
        if (run == null) {
            throw BizException.notFound("运行记录不存在: " + runId);
        }
        Long exists = artifactMapper.selectCount(new LambdaQueryWrapper<BuildArtifactEntity>()
                .eq(BuildArtifactEntity::getRunId, runId)
                .eq(BuildArtifactEntity::getObjectKey, registration.objectKey()));
        if (exists != null && exists > 0) {
            return;
        }
        BuildArtifactEntity artifact = new BuildArtifactEntity();
        artifact.setRunId(runId);
        artifact.setFileName(registration.fileName());
        artifact.setObjectKey(registration.objectKey());
        artifact.setSize(registration.size());
        artifact.setSha256(registration.sha256());
        artifact.setContentType(registration.contentType());
        artifact.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(artifact);
    }

    private RunStatus parse(String status) {
        try {
            return RunStatus.valueOf(status);
        } catch (RuntimeException ex) {
            throw BizException.badRequest("非法运行状态: " + status);
        }
    }
}
