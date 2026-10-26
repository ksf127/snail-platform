package com.snail.platform.common.build;

import java.util.List;
import java.util.Map;

/** worker 执行一次构建所需的只读上下文，由 control 内部接口下发。 */
public record InternalRunContext(
        Long runId,
        Long pipelineId,
        String projectKey,
        String repoUrl,
        String branch,
        String commitSha,
        String executorType,
        List<BuildStep> steps,
        /** 私有仓库 clone 用户名，公开仓库为 null。 */
        String repoUsername,
        /** 私有仓库 clone token，公开仓库为 null；worker 不得写入日志。 */
        String repoToken,
        /** 项目构建变量，已解密，仅注入容器环境变量，不得写入日志。 */
        Map<String, String> secrets
) {
}
