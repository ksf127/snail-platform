package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnvironmentRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotBlank(message = "环境编码不能为空") String name,
        @NotBlank(message = "环境名称不能为空") String displayName,
        @NotBlank(message = "K8s namespace 不能为空") String namespace,
        Integer defaultReplicas,
        String description,
        Integer sortOrder,
        Boolean enabled
) {
}
