package com.company.platform.worker.executor;

import com.company.platform.common.build.BuildStep;
import com.company.platform.common.model.ExecutorType;
import com.company.platform.worker.build.BuildContext;
import com.company.platform.worker.build.LogSink;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 通过 Jenkins 远端 Job 执行构建。约定 Job 接收 REPO_URL / BRANCH / COMMIT_SHA / RUN_ID 参数，
 * 并归档产物；worker 轮询 lastBuild 后拉取 consoleText 与归档产物。
 */
@Component
public class JenkinsBuildExecutor implements BuildExecutor {

    private final String url;
    private final String user;
    private final String token;
    private final String job;
    private final long pollIntervalSeconds;
    private final long timeoutSeconds;
    private final RestClient restClient;

    public JenkinsBuildExecutor(@Value("${platform.jenkins.url:}") String url,
                                @Value("${platform.jenkins.user:}") String user,
                                @Value("${platform.jenkins.token:}") String token,
                                @Value("${platform.jenkins.job:}") String job,
                                @Value("${platform.executor.poll-interval-seconds:3}") long pollIntervalSeconds,
                                @Value("${platform.executor.timeout-seconds:1800}") long timeoutSeconds) {
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.user = user == null ? "" : user.trim();
        this.token = token == null ? "" : token.trim();
        this.job = job == null ? "" : job.trim();
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.timeoutSeconds = timeoutSeconds;
        this.restClient = buildClient();
    }

    @Override
    public ExecutorType type() {
        return ExecutorType.JENKINS;
    }

    @Override
    public void execute(BuildContext context, LogSink sink) throws Exception {
        if (url.isBlank() || job.isBlank()) {
            throw new IllegalStateException("Jenkins 未配置（JENKINS_URL / JENKINS_JOB），无法使用 JENKINS 执行器");
        }
        int previous = lastBuildNumber();
        sink.line("[jenkins] 触发 Job " + job + "，参数 RUN_ID=" + context.getRunId());
        restClient.post()
                .uri(builder -> builder.path("/job/{job}/buildWithParameters")
                        .queryParam("REPO_URL", nullSafe(context.getRepoUrl()))
                        .queryParam("BRANCH", nullSafe(context.getBranch()))
                        .queryParam("COMMIT_SHA", nullSafe(context.getCommitSha()))
                        .queryParam("RUN_ID", context.getRunId())
                        .build(job))
                .retrieve()
                .toBodilessEntity();

        JenkinsBuildInfo info = waitForBuild(previous);
        String console = restClient.get()
                .uri(builder -> builder.path("/job/{job}/{number}/consoleText")
                        .build(job, info.number()))
                .retrieve()
                .body(String.class);
        if (console != null) {
            for (String line : console.split("\\R")) {
                sink.line(line);
            }
        }
        if (!"SUCCESS".equalsIgnoreCase(info.result())) {
            throw new IllegalStateException("Jenkins 构建 #" + info.number() + " 结果: "
                    + (info.result() == null ? "UNKNOWN" : info.result()));
        }
        sink.line("[jenkins] 构建 #" + info.number() + " 成功");
        downloadArtifacts(context, sink, info);
    }

    private JenkinsBuildInfo waitForBuild(int previous) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            JenkinsBuildInfo info = restClient.get()
                    .uri(builder -> builder.path("/job/{job}/lastBuild/api/json")
                            .queryParam("tree", "number,building,result,artifacts[fileName,relativePath]")
                            .build(job))
                    .retrieve()
                    .body(JenkinsBuildInfo.class);
            if (info != null && info.number() > previous && !info.building()) {
                return info;
            }
            Thread.sleep(pollIntervalSeconds * 1000);
        }
        throw new IllegalStateException("等待 Jenkins 构建完成超时（" + timeoutSeconds + " 秒）");
    }

    private int lastBuildNumber() {
        JenkinsBuildInfo info = restClient.get()
                .uri(builder -> builder.path("/job/{job}/lastBuild/api/json")
                        .queryParam("tree", "number")
                        .build(job))
                .retrieve()
                .body(JenkinsBuildInfo.class);
        return info == null ? 0 : info.number();
    }

    private void downloadArtifacts(BuildContext context, LogSink sink, JenkinsBuildInfo info) {
        if (info.artifacts() == null || info.artifacts().isEmpty()) {
            return;
        }
        List<BuildStep> steps = context.getSteps() == null ? List.of() : context.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            BuildStep step = steps.get(i);
            if (step.getArtifacts() == null) {
                continue;
            }
            for (String pattern : step.getArtifacts()) {
                if (pattern == null || pattern.isBlank()) {
                    continue;
                }
                PathMatcher matcher = FileSystems.getDefault()
                        .getPathMatcher("glob:" + pattern.trim().replace('/', File.separatorChar));
                for (JenkinsArtifact artifact : info.artifacts()) {
                    Path relative = Path.of(artifact.relativePath());
                    if (!matcher.matches(relative)) {
                        continue;
                    }
                    try {
                        byte[] bytes = restClient.get()
                                .uri(builder -> builder.path("/job/{job}/{number}/artifact/{path}")
                                        .build(job, info.number(), artifact.relativePath()))
                                .accept(MediaType.APPLICATION_OCTET_STREAM)
                                .retrieve()
                                .body(byte[].class);
                        Path target = context.getArtifactDir()
                                .resolve("step-" + (i + 1))
                                .resolve(relative);
                        Files.createDirectories(target.getParent());
                        Files.write(target, bytes == null ? new byte[0] : bytes,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        sink.line("[jenkins] 下载产物 " + artifact.relativePath());
                    } catch (Exception ex) {
                        sink.line("[jenkins] 警告: 下载产物失败 " + artifact.relativePath()
                                + " - " + ex.getMessage());
                    }
                }
            }
        }
    }

    private RestClient buildClient() {
        RestClient.Builder builder = RestClient.builder();
        if (!url.isBlank()) {
            builder.baseUrl(url);
        }
        if (!user.isBlank() && !token.isBlank()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(user, token));
        }
        return builder.build();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JenkinsBuildInfo(int number, boolean building, String result, List<JenkinsArtifact> artifacts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record JenkinsArtifact(String fileName, String relativePath) {
    }
}
