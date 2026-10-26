package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BranchRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotBlank(message = "分支不能为空") String branch,
        String team,
        Long ownerId,
        String description
) {
}
