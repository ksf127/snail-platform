package com.snail.platform.worker.service;

import com.snail.platform.common.build.ArtifactRegistration;
import com.snail.platform.common.build.BuildMessage;
import com.snail.platform.common.build.InternalRunContext;
import com.snail.platform.common.build.RunStatusUpdate;
import com.snail.platform.common.model.RunStatus;
import com.snail.platform.worker.build.ArtifactCollector;
import com.snail.platform.worker.build.BuildContext;
import com.snail.platform.worker.build.FileLogSink;
import com.snail.platform.worker.build.LogSink;
import com.snail.platform.worker.build.MaskingLogSink;
import com.snail.platform.worker.client.ControlClient;
import com.snail.platform.worker.executor.BuildExecutor;
import com.snail.platform.worker.executor.ExecutorResolver;
import com.snail.platform.worker.storage.ArtifactStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 构建主流程：拉上下文 → RUNNING → 执行 → 收集并上传产物 → 上传日志 → 终态。
 * 构建本身的失败会被记录为 FAILED；只有控制面不可用等基础设施异常才会抛出，由队列转入死信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final ControlClient controlClient;
    private final ExecutorResolver executorResolver;
    private final ArtifactCollector artifactCollector;
    private final ArtifactStore artifactStore;

    @Value("${platform.workspace.dir:./workspace}")
    private String workspaceDir;

    public void execute(BuildMessage message) {
        Long runId = message.runId();
        InternalRunContext remote = controlClient.fetchContext(runId);
        Path root = Paths.get(workspaceDir).toAbsolutePath().normalize().resolve("run-" + runId);
        Path sourceDir = root.resolve("source");
        Path artifactDir = root.resolve("artifacts");
        Path logFile = root.resolve("build.log");

        String error = null;
        boolean success = false;
        try {
            Files.createDirectories(sourceDir);
            Files.createDirectories(artifactDir);
        } catch (IOException ex) {
            throw new IllegalStateException("创建构建工作目录失败: " + root, ex);
        }

        BuildContext context = BuildContext.builder()
                .runId(runId)
                .pipelineId(remote.pipelineId())
                .projectKey(remote.projectKey())
                .repoUrl(remote.repoUrl())
                .repoUsername(remote.repoUsername())
                .repoToken(remote.repoToken())
                .secrets(remote.secrets() == null ? java.util.Map.of() : remote.secrets())
                .branch(remote.branch())
                .commitSha(remote.commitSha())
                .rootDir(root)
                .sourceDir(sourceDir)
                .artifactDir(artifactDir)
                .steps(remote.steps())
                .build();

        try (FileLogSink fileSink = new FileLogSink(logFile)) {
            LogSink sink = new MaskingLogSink(fileSink, remote.repoToken(), context.getSecrets());
            sink.line("===== 构建开始 =====");
            sink.line("runId=" + runId + ", pipelineId=" + remote.pipelineId()
                    + ", project=" + remote.projectKey() + ", branch=" + remote.branch());
            reportStatus(runId, new RunStatusUpdate(RunStatus.RUNNING.name(), null, null));
            try {
                BuildExecutor executor = executorResolver.resolve(remote.executorType());
                sink.line("执行器: " + executor.type());
                if (!context.getSecrets().isEmpty()) {
                    sink.line("[env] 已注入 " + context.getSecrets().size() + " 个项目构建变量");
                }
                executor.execute(context, sink);
                artifactCollector.collect(remote.steps(), sourceDir, artifactDir, sink);
                uploadArtifacts(runId, artifactDir, sink);
                success = true;
                sink.line("===== 构建成功 =====");
            } catch (Exception ex) {
                error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                log.warn("构建失败 run={}: {}", runId, error, ex);
                sink.line("===== 构建失败 =====");
                sink.line("[error] " + error);
            }
        } catch (IOException ex) {
            log.error("写入构建日志失败 run={}", runId, ex);
            if (error == null) {
                error = "写入构建日志失败: " + ex.getMessage();
            }
        }

        String logObject = null;
        try {
            logObject = artifactStore.uploadLog(runId, logFile);
        } catch (Exception ex) {
            log.warn("上传构建日志失败 run={}: {}", runId, ex.getMessage());
        }

        RunStatusUpdate update = success
                ? new RunStatusUpdate(RunStatus.SUCCESS.name(), null, logObject)
                : new RunStatusUpdate(RunStatus.FAILED.name(), error, logObject);
        reportStatus(runId, update);
        cleanup(root);
    }

    private void uploadArtifacts(Long runId, Path artifactDir, LogSink sink) throws Exception {
        if (!Files.isDirectory(artifactDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(artifactDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = artifactDir.relativize(file).toString().replace('\\', '/');
                try {
                    ArtifactRegistration registration = artifactStore.uploadArtifact(runId, file, relative);
                    controlClient.registerArtifact(runId, registration);
                    sink.line("[artifact] 已上传 " + relative + " (" + registration.size() + " bytes)");
                } catch (Exception ex) {
                    throw new IllegalStateException("上传产物 " + relative + " 失败: " + ex.getMessage(), ex);
                }
            }
        }
    }

    private void reportStatus(Long runId, RunStatusUpdate update) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                controlClient.updateStatus(runId, update);
                return;
            } catch (RuntimeException ex) {
                log.warn("回报运行状态失败 run={} status={} attempt={}: {}",
                        runId, update.status(), attempt, ex.getMessage());
                if (attempt == 3) {
                    log.error("回报运行状态最终失败 run={} status={}", runId, update.status(), ex);
                    return;
                }
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void cleanup(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 清理失败不影响构建结果
                        }
                    });
        } catch (IOException ex) {
            log.warn("清理工作目录失败 {}: {}", root, ex.getMessage());
        }
    }
}
