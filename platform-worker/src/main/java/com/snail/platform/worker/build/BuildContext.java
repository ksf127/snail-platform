package com.snail.platform.worker.build;

import com.snail.platform.common.build.BuildStep;
import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;
import java.util.List;

/**
 * 一次构建执行所需的全部上下文。rootDir 是本次运行的独立工作目录。
 */
@Getter
@Builder
public class BuildContext {

    private final Long runId;
    private final Long pipelineId;
    private final String projectKey;
    private final String repoUrl;
    private final String branch;
    private final String commitSha;
    private final Path rootDir;
    private final Path sourceDir;
    private final Path artifactDir;
    private final List<BuildStep> steps;
    private final LogSink log;
}
