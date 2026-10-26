package com.snail.platform.control.dto;

/** 流水线步骤模板建议：前端据此预填新建表单，用户仍可继续编辑 JSON。 */
public record PipelineTemplateView(
        String stack,
        String side,
        String name,
        String branch,
        String triggerType,
        String executorType,
        String definitionJson,
        String source
) {
}
