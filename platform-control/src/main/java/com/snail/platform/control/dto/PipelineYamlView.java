package com.snail.platform.control.dto;

/** YAML 配置视图：definitionJson 便于前端回填 JSON 编辑器，source 说明本次解析来源。 */
public record PipelineYamlView(
        String yaml,
        String definitionJson,
        String configPath,
        String source
) {
}
