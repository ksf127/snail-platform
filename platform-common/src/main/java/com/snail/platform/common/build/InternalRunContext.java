package com.snail.platform.common.build;

import java.util.List;

/** worker 执行一次构建所需的只读上下文，由 control 内部接口下发。 */
public record InternalRunContext(
        Long runId,
        Long pipelineId,
        String projectKey,
        String repoUrl,
        String branch,
        String commitSha,
        String executorType,
        List<BuildStep> steps
) {
}
