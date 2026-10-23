package com.company.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.platform.common.api.BizException;
import com.company.platform.common.build.BuildMessage;
import com.company.platform.common.build.BuildStep;
import com.company.platform.common.build.PipelineDefinition;
import com.company.platform.common.model.ExecutorType;
import com.company.platform.common.model.RunStatus;
import com.company.platform.common.model.TriggerType;
import com.company.platform.control.dto.PipelineRequest;
import com.company.platform.control.entity.PipelineEntity;
import com.company.platform.control.entity.PipelineRunEntity;
import com.company.platform.control.entity.RepoEntity;
import com.company.platform.control.integration.BuildMessagePublisher;
import com.company.platform.control.mapper.PipelineMapper;
import com.company.platform.control.mapper.PipelineRunMapper;
import com.company.platform.control.mapper.RepoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineMapper pipelineMapper;
    private final PipelineRunMapper pipelineRunMapper;
    private final RepoMapper repoMapper;
    private final ProjectService projectService;
    private final BuildMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    @Value("${platform.executor.default:DOCKER}")
    private String defaultExecutor;

    public List<PipelineEntity> list(Long projectId) {
        LambdaQueryWrapper<PipelineEntity> wrapper = new LambdaQueryWrapper<PipelineEntity>()
                .orderByDesc(PipelineEntity::getId);
        if (projectId != null) {
            wrapper.eq(PipelineEntity::getProjectId, projectId);
        }
        return pipelineMapper.selectList(wrapper);
    }

    public PipelineEntity get(Long id) {
        PipelineEntity pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw BizException.notFound("流水线不存在: " + id);
        }
        return pipeline;
    }

    public PipelineEntity create(PipelineRequest request) {
        projectService.get(request.projectId());
        PipelineDefinition definition = validate(request);
        validateRepo(request.projectId(), request.repoId());

        PipelineEntity pipeline = new PipelineEntity();
        apply(pipeline, request, definition);
        pipeline.setCreatedAt(LocalDateTime.now());
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.insert(pipeline);
        return pipeline;
    }

    public PipelineEntity update(Long id, PipelineRequest request) {
        PipelineEntity pipeline = get(id);
        if (!pipeline.getProjectId().equals(request.projectId())) {
            throw BizException.badRequest("不允许将流水线移动到其他项目");
        }
        PipelineDefinition definition = validate(request);
        validateRepo(request.projectId(), request.repoId());
        apply(pipeline, request, definition);
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
        return pipeline;
    }

    public void delete(Long id) {
        get(id);
        pipelineMapper.deleteById(id);
    }

    /**
     * 触发一次构建：先落 run（QUEUED），再投递 MQ。
     * 投递失败会把 run 标记为 FAILED，避免出现永远 QUEUED 的幽灵任务。
     */
    public PipelineRunEntity trigger(Long pipelineId, TriggerType triggerType, String branch,
                                     String commitSha, String triggeredBy) {
        PipelineEntity pipeline = get(pipelineId);
        if (!Boolean.TRUE.equals(pipeline.getEnabled())) {
            throw BizException.badRequest("流水线已禁用，无法触发");
        }
        RepoEntity repo = pipeline.getRepoId() == null ? null : repoMapper.selectById(pipeline.getRepoId());
        if (repo == null || !StringUtils.hasText(repo.getCloneUrl())) {
            throw BizException.badRequest("流水线未绑定可用仓库（cloneUrl 为空）");
        }

        PipelineRunEntity run = new PipelineRunEntity();
        run.setPipelineId(pipelineId);
        run.setBuildNo(nextBuildNo(pipelineId));
        run.setStatus(RunStatus.QUEUED.name());
        run.setBranch(StringUtils.hasText(branch) ? branch : pipeline.getBranch());
        run.setCommitSha(commitSha);
        run.setTriggerType(triggerType.name());
        run.setTriggeredBy(triggeredBy);
        run.setCreatedAt(LocalDateTime.now());
        pipelineRunMapper.insert(run);

        try {
            messagePublisher.publish(new BuildMessage(run.getId(), pipelineId));
        } catch (RuntimeException ex) {
            run.setStatus(RunStatus.FAILED.name());
            run.setErrorMessage("投递构建消息失败: " + ex.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            pipelineRunMapper.updateById(run);
            throw BizException.unavailable("构建任务入队失败: " + ex.getMessage());
        }
        return run;
    }

    /** Webhook push 事件：命中同仓库、同分支（支持 *）、启用且触发方式为 WEBHOOK 的流水线。 */
    public List<Long> triggerWebhook(RepoEntity repo, String branch, String commitSha) {
        List<PipelineEntity> pipelines = pipelineMapper.selectList(new LambdaQueryWrapper<PipelineEntity>()
                .eq(PipelineEntity::getRepoId, repo.getId())
                .eq(PipelineEntity::getEnabled, true)
                .eq(PipelineEntity::getTriggerType, TriggerType.WEBHOOK.name()));
        return pipelines.stream()
                .filter(p -> "*".equals(p.getBranch()) || p.getBranch().equals(branch))
                .map(p -> trigger(p.getId(), TriggerType.WEBHOOK, branch, commitSha, "gitea-webhook").getId())
                .toList();
    }

    private int nextBuildNo(Long pipelineId) {
        PipelineRunEntity last = pipelineRunMapper.selectOne(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getPipelineId, pipelineId)
                .orderByDesc(PipelineRunEntity::getBuildNo)
                .last("limit 1"));
        return last == null || last.getBuildNo() == null ? 1 : last.getBuildNo() + 1;
    }

    private void apply(PipelineEntity pipeline, PipelineRequest request, PipelineDefinition definition) {
        pipeline.setProjectId(request.projectId());
        pipeline.setRepoId(request.repoId());
        pipeline.setName(request.name());
        pipeline.setBranch(request.branch());
        pipeline.setTriggerType(parseEnum(TriggerType.class, request.triggerType(), "触发方式").name());
        String executor = StringUtils.hasText(request.executorType())
                ? request.executorType() : defaultExecutor;
        pipeline.setExecutorType(parseEnum(ExecutorType.class, executor, "执行器类型").name());
        pipeline.setDefinitionJson(write(definition));
        pipeline.setEnabled(request.enabled() == null || request.enabled());
    }

    private void validateRepo(Long projectId, Long repoId) {
        if (repoId == null) {
            return;
        }
        RepoEntity repo = repoMapper.selectById(repoId);
        if (repo == null) {
            throw BizException.badRequest("仓库不存在: " + repoId);
        }
        if (!repo.getProjectId().equals(projectId)) {
            throw BizException.badRequest("仓库不属于该项目");
        }
    }

    private PipelineDefinition validate(PipelineRequest request) {
        parseEnum(TriggerType.class, request.triggerType(), "触发方式");
        parseEnum(ExecutorType.class, request.executorType() == null ? defaultExecutor : request.executorType(),
                "执行器类型");
        PipelineDefinition definition;
        try {
            definition = objectMapper.readValue(request.definitionJson(), PipelineDefinition.class);
        } catch (JsonProcessingException ex) {
            throw BizException.badRequest("流水线定义不是合法 JSON: " + ex.getOriginalMessage());
        }
        if (definition.getSteps() == null || definition.getSteps().isEmpty()) {
            throw BizException.badRequest("流水线至少需要一个 step");
        }
        for (int i = 0; i < definition.getSteps().size(); i++) {
            BuildStep step = definition.getSteps().get(i);
            if (!StringUtils.hasText(step.getImage())) {
                throw BizException.badRequest("第 " + (i + 1) + " 个 step 缺少 image");
            }
            if (step.getCommands() == null || step.getCommands().isEmpty()) {
                throw BizException.badRequest("第 " + (i + 1) + " 个 step 缺少 commands");
            }
            if (!StringUtils.hasText(step.getName())) {
                step.setName("step-" + (i + 1));
            }
            if (!StringUtils.hasText(step.getWorkDir())) {
                step.setWorkDir("/workspace");
            }
            if (step.getArtifacts() == null) {
                step.setArtifacts(List.of());
            }
        }
        return definition;
    }

    private String write(PipelineDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException ex) {
            throw BizException.badRequest("流水线定义序列化失败");
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw BizException.badRequest("非法" + label + ": " + value);
        }
    }
}
