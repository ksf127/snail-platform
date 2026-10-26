package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 单仓（前后端在同一个仓库）一键生成流水线的请求。
 * frontendDir 为仓库内前端子目录（如 web），后端默认按仓库根目录构建。
 */
public record MonorepoPipelineRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotNull(message = "仓库 ID 不能为空") Long repoId,
        String branch,
        String triggerType,
        String frontendDir,
        String backendStack
) {
}
