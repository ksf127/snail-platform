package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建/更新流水线。
 * definitionJson 与 configPath 至少提供一个：configPath 非空表示以仓库内 YAML 为准。
 */
public record PipelineRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        Long repoId,
        @NotBlank(message = "流水线名称不能为空") String name,
        @NotBlank(message = "分支不能为空") String branch,
        @NotBlank(message = "触发方式不能为空") String triggerType,
        @NotBlank(message = "执行器类型不能为空") String executorType,
        String side,
        String definitionJson,
        Boolean enabled,
        String configPath
) {
}
