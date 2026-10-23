package com.company.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PipelineRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        Long repoId,
        @NotBlank(message = "流水线名称不能为空") String name,
        @NotBlank(message = "分支不能为空") String branch,
        @NotBlank(message = "触发方式不能为空") String triggerType,
        @NotBlank(message = "执行器类型不能为空") String executorType,
        @NotBlank(message = "流水线定义不能为空") String definitionJson,
        Boolean enabled
) {
}
