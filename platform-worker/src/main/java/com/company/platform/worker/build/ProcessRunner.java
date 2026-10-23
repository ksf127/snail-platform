package com.company.platform.worker.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 运行外部命令并把 stdout/stderr 逐行转发到 LogSink。
 */
public final class ProcessRunner {

    private ProcessRunner() {
    }

    public static int run(List<String> command, Path workingDir, LogSink log, long timeoutSeconds)
            throws IOException, InterruptedException {
        log.line("$ " + String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        Thread reader = new Thread(() -> {
            try (BufferedReader reader1 = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader1.readLine()) != null) {
                    log.line(line);
                }
            } catch (IOException ex) {
                log.line("[process] 读取输出失败: " + ex.getMessage());
            }
        }, "process-output-" + command.get(0));
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            reader.join(2000);
            throw new IllegalStateException("命令执行超时（" + timeoutSeconds + " 秒）: " + command.get(0));
        }
        reader.join(5000);
        return process.exitValue();
    }
}
