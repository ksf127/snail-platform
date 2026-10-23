package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.model.RunStatus;
import com.snail.platform.control.dto.ArtifactView;
import com.snail.platform.control.dto.RunView;
import com.snail.platform.control.entity.BuildArtifactEntity;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.PipelineRunEntity;
import com.snail.platform.control.integration.ArtifactStorage;
import com.snail.platform.control.mapper.BuildArtifactMapper;
import com.snail.platform.control.mapper.PipelineMapper;
import com.snail.platform.control.mapper.PipelineRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RunService {

    private final PipelineRunMapper runMapper;
    private final PipelineMapper pipelineMapper;
    private final BuildArtifactMapper artifactMapper;
    private final ArtifactStorage artifactStorage;

    public List<RunView> list(Long pipelineId, int limit) {
        LambdaQueryWrapper<PipelineRunEntity> wrapper = new LambdaQueryWrapper<PipelineRunEntity>()
                .orderByDesc(PipelineRunEntity::getId);
        if (pipelineId != null) {
            wrapper.eq(PipelineRunEntity::getPipelineId, pipelineId);
        }
        wrapper.last("limit " + Math.max(1, Math.min(limit, 200)));
        List<PipelineRunEntity> runs = runMapper.selectList(wrapper);
        if (runs.isEmpty()) {
            return List.of();
        }
        List<Long> pipelineIds = runs.stream().map(PipelineRunEntity::getPipelineId).distinct().toList();
        Map<Long, PipelineEntity> pipelines = pipelineMapper.selectBatchIds(pipelineIds).stream()
                .collect(Collectors.toMap(PipelineEntity::getId, Function.identity()));
        return runs.stream().map(run -> toView(run, pipelines.get(run.getPipelineId()))).toList();
    }

    public RunView get(Long id) {
        PipelineRunEntity run = getEntity(id);
        PipelineEntity pipeline = pipelineMapper.selectById(run.getPipelineId());
        return toView(run, pipeline);
    }

    public PipelineRunEntity getEntity(Long id) {
        PipelineRunEntity run = runMapper.selectById(id);
        if (run == null) {
            throw BizException.notFound("运行记录不存在: " + id);
        }
        return run;
    }

    /**
     * 取消只改变控制面状态。已经进入执行器的容器不会自动停止，
     * 生产环境应配合 worker 主动 kill 容器（MVP 未实现运行中强杀）。
     */
    public RunView cancel(Long id) {
        PipelineRunEntity run = getEntity(id);
        if (RunStatus.SUCCESS.name().equals(run.getStatus())
                || RunStatus.FAILED.name().equals(run.getStatus())
                || RunStatus.CANCELED.name().equals(run.getStatus())) {
            throw BizException.badRequest("运行已结束，无法取消");
        }
        run.setStatus(RunStatus.CANCELED.name());
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorMessage("用户取消");
        runMapper.updateById(run);
        return toView(run, pipelineMapper.selectById(run.getPipelineId()));
    }

    public String logUrl(Long runId) {
        PipelineRunEntity run = getEntity(runId);
        if (run.getLogObject() == null || run.getLogObject().isBlank()) {
            throw BizException.notFound("日志尚未生成");
        }
        return artifactStorage.presignedGetUrl(run.getLogObject());
    }

    public List<ArtifactView> artifacts(Long runId) {
        getEntity(runId);
        return artifactMapper.selectList(new LambdaQueryWrapper<BuildArtifactEntity>()
                        .eq(BuildArtifactEntity::getRunId, runId)
                        .orderByAsc(BuildArtifactEntity::getId))
                .stream().map(this::toView).toList();
    }

    public String artifactUrl(Long artifactId) {
        BuildArtifactEntity artifact = artifactMapper.selectById(artifactId);
        if (artifact == null) {
            throw BizException.notFound("制品不存在: " + artifactId);
        }
        return artifactStorage.presignedGetUrl(artifact.getObjectKey());
    }

    private RunView toView(PipelineRunEntity run, PipelineEntity pipeline) {
        return new RunView(run.getId(), run.getPipelineId(),
                pipeline == null ? "unknown" : pipeline.getName(),
                run.getBuildNo(), run.getStatus(), run.getCommitSha(), run.getBranch(),
                run.getTriggerType(), run.getTriggeredBy(), run.getStartedAt(), run.getFinishedAt(),
                run.getLogObject(), run.getErrorMessage(), run.getCreatedAt());
    }

    private ArtifactView toView(BuildArtifactEntity artifact) {
        return new ArtifactView(artifact.getId(), artifact.getRunId(), artifact.getFileName(),
                artifact.getObjectKey(), artifact.getSize(), artifact.getSha256(),
                artifact.getContentType(), artifact.getCreatedAt());
    }
}
