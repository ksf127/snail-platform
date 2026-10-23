package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRepoRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotBlank(message = "仓库名不能为空")
        @Size(max = 64, message = "仓库名最长 64 字符")
        String name,
        String description,
        Boolean privateRepo,
        String defaultBranch,
        String webhookSecret
) {
}
