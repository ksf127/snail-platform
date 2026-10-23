package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BindRepoRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotBlank(message = "仓库名不能为空") String name,
        @NotBlank(message = "cloneUrl 不能为空") String cloneUrl,
        String defaultBranch,
        String webhookSecret
) {
}
