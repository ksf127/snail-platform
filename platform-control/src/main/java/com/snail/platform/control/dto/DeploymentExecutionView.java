package com.snail.platform.control.dto;

import com.snail.platform.control.entity.DeploymentEntity;

/** mode=APPLIED 表示已调用 K8s；mode=PREVIEW 表示未配置 K8s，仅返回将执行的命令。 */
public record DeploymentExecutionView(
        DeploymentEntity deployment,
        String mode,
        String commandPreview,
        String message
) {
}
