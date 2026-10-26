package com.snail.platform.worker.executor;

import com.snail.platform.common.build.BuildStep;
import com.snail.platform.common.model.ExecutorType;
import com.snail.platform.worker.build.BuildContext;
import com.snail.platform.worker.build.LogSink;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * 通过 Jenkins 远端 Job 执行构建。约定 Job 接收 REPO_URL / BRANCH / COMMIT_SHA / RUN_ID 参数，
 * 并通过 GIT_USERNAME / GIT_TOKEN / SECRETS_JSON 接收仓库凭据与项目构建变量；构建完成后归档产物，
 * worker 轮询 lastBuild 后拉取 consoleText 与归档产物。
 *
 * <p>参数统一使用表单体提交（不使用 URL query），避免凭据出现在 Jenkins / 反向代理访问日志中。
 */
@Component
public class JenkinsBuildExecutor implements BuildExecutor {

    private final String url;
    private final String user;
    private final String token;
    private final String job;
    private final long pollIntervalSeconds;
    private final long timeoutSeconds;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public JenkinsBuildExecutor(@Value("${platform.jenkins.url:}") String url,
                                @Value("${platform.jenkins.user:}") String user,
                                @Value("${platform.jenkins.token:}") String token,
                                @Value("${platform.jenkins.job:}") String job,
                                @Value("${platform.executor.poll-interval-seconds:3}") long pollIntervalSeconds,
                                @Value("${platform.executor.timeout-seconds:1800}") long timeoutSeconds,
                                ObjectMapper objectMapper) {
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        this.user = user == null ? "" : user.trim();
        this.token = token == null ? "" : token.trim();
        this.job = job == null ? "" : job.trim();
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
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
        int secretCount = context.getSecrets() == null ? 0 : context.getSecrets().size();
        sink.line("[jenkins] 触发 Job " + job + "，参数 RUN_ID=" + context.getRunId()
                + (secretCount > 0 ? "，注入构建变量 " + secretCount + " 个" : "")
                + (hasRepoCredential(context) ? "，注入仓库凭据" : ""));
        restClient.post()
                .uri(builder -> builder.path("/job/{job}/buildWithParameters").build(job))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(parameters(context))
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

    private MultiValueMap<String, String> parameters(BuildContext context) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("REPO_URL", nullSafe(context.getRepoUrl()));
        form.add("BRANCH", nullSafe(context.getBranch()));
        form.add("COMMIT_SHA", nullSafe(context.getCommitSha()));
        form.add("RUN_ID", String.valueOf(context.getRunId()));
        if (hasRepoCredential(context)) {
            form.add("GIT_USERNAME", nullSafe(context.getRepoUsername()));
            form.add("GIT_TOKEN", context.getRepoToken().trim());
        }
        Map<String, String> secrets = context.getSecrets();
        if (secrets != null && !secrets.isEmpty()) {
            form.add("SECRETS_JSON", objectMapper.writeValueAsString(secrets));
        }
        return form;
    }

    private boolean hasRepoCredential(BuildContext context) {
        return context.getRepoToken() != null && !context.getRepoToken().isBlank();
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
