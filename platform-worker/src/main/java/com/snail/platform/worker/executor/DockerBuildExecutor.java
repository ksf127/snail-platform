package com.snail.platform.worker.executor;

import com.snail.platform.common.build.BuildStep;
import com.snail.platform.common.model.ExecutorType;
import com.snail.platform.worker.build.BuildContext;
import com.snail.platform.worker.build.LogSink;
import com.snail.platform.worker.build.ProcessRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于本机 Docker 的执行器。worker 需要挂载 docker.sock，且构建会获得宿主机 Docker 权限，
 * 因此只能把不可信代码放在隔离的 worker 节点上执行。
 */
@Component
public class DockerBuildExecutor implements BuildExecutor {

    private static final String WORKSPACE = "/workspace";

    private final long timeoutSeconds;

    public DockerBuildExecutor(@Value("${platform.executor.timeout-seconds:1800}") long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ExecutorType type() {
        return ExecutorType.DOCKER;
    }

    @Override
    public void execute(BuildContext context, LogSink sink) throws Exception {
        if (context.getRepoUrl() == null || context.getRepoUrl().isBlank()) {
            throw new IllegalStateException("仓库未绑定 cloneUrl，无法执行构建");
        }
        Files.createDirectories(context.getSourceDir());

        checkDocker(sink);
        cloneRepository(context, sink);

        List<BuildStep> steps = context.getSteps() == null ? List.of() : context.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            BuildStep step = steps.get(i);
            String workDir = step.getWorkDir() == null || step.getWorkDir().isBlank()
                    ? "/workspace" : step.getWorkDir();
            String script = String.join("\n", step.getCommands());
            sink.line("[docker] 步骤 " + (i + 1) + "/" + steps.size() + ": " + step.getName());
            List<String> command = new ArrayList<>(List.of(
                    "docker", "run", "--rm",
                    // 源码永远挂到 /workspace，工作目录再切到 workDir，
                    // 这样单仓场景下 workDir=/workspace/web 才能用到仓库里的子目录。
                    "-v", context.getSourceDir().toAbsolutePath() + ":" + WORKSPACE,
                    "-w", workDir,
                    step.getImage(),
                    "sh", "-lc", script));
            int exit = ProcessRunner.run(command, context.getRootDir(), sink, timeoutSeconds);
            if (exit != 0) {
                throw new IllegalStateException("步骤 " + step.getName() + " 执行失败，退出码 " + exit);
            }
        }
        sink.line("[docker] 所有步骤执行完成");
    }

    private void checkDocker(LogSink sink) throws Exception {
        int exit = ProcessRunner.run(List.of("docker", "version", "--format", "{{.Server.Version}}"),
                Path.of("."), sink, Math.min(60, timeoutSeconds));
        if (exit != 0) {
            throw new IllegalStateException("Docker 不可用，请确认 worker 已挂载 /var/run/docker.sock");
        }
    }

    private void cloneRepository(BuildContext context, LogSink sink) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "clone", "--depth", "1"));
        if (context.getBranch() != null && !context.getBranch().isBlank()) {
            command.add("--branch");
            command.add(context.getBranch());
        }
        command.add(context.getRepoUrl());
        command.add(context.getSourceDir().toAbsolutePath().toString());
        sink.line("[docker] 克隆仓库 " + context.getRepoUrl() + " (分支 " + context.getBranch() + ")");
        int exit = ProcessRunner.run(command, context.getRootDir(), sink, timeoutSeconds);
        if (exit != 0) {
            throw new IllegalStateException("git clone 失败，退出码 " + exit);
        }
        if (context.getCommitSha() != null && !context.getCommitSha().isBlank()) {
            List<String> checkout = List.of("git", "-C", context.getSourceDir().toAbsolutePath().toString(),
                    "checkout", context.getCommitSha());
            int checkoutExit = ProcessRunner.run(checkout, context.getRootDir(), sink, 120);
            if (checkoutExit != 0) {
                sink.line("[docker] 警告: 无法切换到提交 " + context.getCommitSha() + "，继续使用分支最新提交");
            }
        }
    }
}
