package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeploymentRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        Long runId,
        @NotBlank(message = "环境不能为空") String environment,
        @NotBlank(message = "镜像不能为空") String image,
        Integer replicas,
        String note
) {
}
