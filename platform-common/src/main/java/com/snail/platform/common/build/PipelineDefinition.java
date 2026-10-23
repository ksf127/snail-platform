package com.snail.platform.common.build;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 持久化在 pipeline.definition_json 中的 DSL。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineDefinition {

    @Builder.Default
    private List<BuildStep> steps = new ArrayList<>();
}
