package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.model.DeploymentEnvironment;
import com.snail.platform.common.model.DeploymentStatus;
import com.snail.platform.control.dto.DeploymentExecutionView;
import com.snail.platform.control.dto.DeploymentRequest;
import com.snail.platform.control.entity.DeploymentEntity;
import com.snail.platform.control.entity.ProjectEntity;
import com.snail.platform.control.integration.K8sClientFactory;
import com.snail.platform.control.integration.K8sDeploymentOperator;
import com.snail.platform.control.mapper.DeploymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentMapper deploymentMapper;
    private final ProjectService projectService;
    private final K8sClientFactory k8sClientFactory;
    private final K8sDeploymentOperator k8sDeploymentOperator;

    public List<DeploymentEntity> list(Long projectId) {
        LambdaQueryWrapper<DeploymentEntity> wrapper = new LambdaQueryWrapper<DeploymentEntity>()
                .orderByDesc(DeploymentEntity::getId);
        if (projectId != null) {
            wrapper.eq(DeploymentEntity::getProjectId, projectId);
        }
        return deploymentMapper.selectList(wrapper);
    }

    public DeploymentEntity create(DeploymentRequest request) {
        projectService.get(request.projectId());
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setProjectId(request.projectId());
        deployment.setRunId(request.runId());
        deployment.setEnvironment(parseEnvironment(request.environment()));
        deployment.setStatus(DeploymentStatus.PENDING.name());
        deployment.setImage(request.image());
        deployment.setReplicas(request.replicas() == null ? 1 : Math.max(1, request.replicas()));
        deployment.setNote(request.note());
        deployment.setCreatedAt(LocalDateTime.now());
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentMapper.insert(deployment);
        return deployment;
    }

    /**
     * 执行发布：K8s 已配置时真实更新 Deployment 镜像；否则只返回命令预览并把发布单标记成功。
     * 预览模式是 MVP 明确的设计边界，响应里的 mode=PREVIEW 会告知调用方。
     */
    public DeploymentExecutionView execute(Long id) {
        DeploymentEntity deployment = deploymentMapper.selectById(id);
        if (deployment == null) {
            throw BizException.notFound("发布单不存在: " + id);
        }
        ProjectEntity project = projectService.get(deployment.getProjectId());
        String target = project.getProjectKey().toLowerCase(Locale.ROOT);
        String namespace = k8sClientFactory.namespace();
        String preview = "kubectl set image deployment/" + target + " " + target + "=" + deployment.getImage()
                + " -n " + namespace + " && kubectl scale deployment/" + target
                + " --replicas=" + deployment.getReplicas() + " -n " + namespace;

        deployment.setStatus(DeploymentStatus.RUNNING.name());
        deployment.setUpdatedAt(LocalDateTime.now());
        deploymentMapper.updateById(deployment);

        if (!k8sClientFactory.isConfigured()) {
            deployment.setStatus(DeploymentStatus.SUCCESS.name());
            deployment.setUpdatedAt(LocalDateTime.now());
            deploymentMapper.updateById(deployment);
            return new DeploymentExecutionView(deployment, "PREVIEW", preview,
                    "K8s 未配置，未实际执行；以上为将要执行的命令。");
        }

        try {
            k8sDeploymentOperator.updateImage(target, deployment.getImage(), deployment.getReplicas());
            deployment.setStatus(DeploymentStatus.SUCCESS.name());
            deployment.setUpdatedAt(LocalDateTime.now());
            deploymentMapper.updateById(deployment);
            return new DeploymentExecutionView(deployment, "APPLIED", null,
                    "已更新 K8s Deployment " + namespace + "/" + target);
        } catch (RuntimeException ex) {
            log.error("执行发布失败 id={}", id, ex);
            deployment.setStatus(DeploymentStatus.FAILED.name());
            deployment.setNote(appendNote(deployment.getNote(), "执行失败: " + ex.getMessage()));
            deployment.setUpdatedAt(LocalDateTime.now());
            deploymentMapper.updateById(deployment);
            return new DeploymentExecutionView(deployment, "FAILED", null,
                    "发布失败: " + ex.getMessage());
        }
    }

    private String parseEnvironment(String environment) {
        try {
            return DeploymentEnvironment.valueOf(environment.toUpperCase(Locale.ROOT)).name();
        } catch (RuntimeException ex) {
            throw BizException.badRequest("非法环境: " + environment);
        }
    }

    private String appendNote(String note, String extra) {
        String base = note == null ? "" : note + " | ";
        String merged = base + extra;
        return merged.length() > 500 ? merged.substring(0, 500) : merged;
    }
}
