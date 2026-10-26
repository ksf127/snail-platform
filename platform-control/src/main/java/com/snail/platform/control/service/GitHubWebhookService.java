package com.snail.platform.control.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.build.RepoUrls;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.GiteaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * GitHub push webhook：使用 X-Hub-Signature-256 验签（HMAC-SHA256 + 仓库各自的 secret）。
 * 支持 https/ssh 形式的 cloneUrl 归一化匹配，ping 事件直接返回空列表。
 */
@Service
@RequiredArgsConstructor
public class GitHubWebhookService {

    private final RepoService repoService;
    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;

    public List<Long> handlePush(String payload, String event, String signature) {
        if ("ping".equalsIgnoreCase(event)) {
            return List.of();
        }
        if (!"push".equalsIgnoreCase(event)) {
            throw BizException.badRequest("不支持的 GitHub 事件: " + event + "，仅支持 push");
        }
        if (payload == null || payload.isBlank()) {
            throw BizException.badRequest("空的 webhook 请求体");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw BizException.badRequest("webhook 请求体不是合法 JSON");
        }

        String cloneUrl = root.path("repository").path("clone_url").asText(null);
        String htmlUrl = root.path("repository").path("html_url").asText(null);
        String sshUrl = root.path("repository").path("ssh_url").asText(null);
        List<RepoEntity> repos = repoService.listAll();

        RepoEntity repo = repos.stream()
                .filter(candidate -> verify(candidate.getWebhookSecret(), payload, signature))
                .filter(candidate -> matches(candidate.getCloneUrl(), cloneUrl, htmlUrl, sshUrl))
                .findFirst()
                .orElseThrow(() -> BizException.unauthorized("GitHub webhook 签名校验失败或仓库未登记"));

        if (root.path("deleted").asBoolean(false)) {
            return List.of();
        }
        String after = root.path("after").asText("");
        if (after.isBlank() || after.chars().allMatch(ch -> ch == '0')) {
            return List.of();
        }
        String ref = root.path("ref").asText("");
        if (!ref.startsWith("refs/heads/")) {
            return List.of();
        }
        String branch = ref.substring("refs/heads/".length());
        return pipelineService.triggerWebhook(repo, branch, after, "github-webhook");
    }

    private boolean verify(String secret, String payload, String signature) {
        if (!org.springframework.util.StringUtils.hasText(secret)
                || !org.springframework.util.StringUtils.hasText(signature)) {
            return false;
        }
        String normalized = signature.startsWith("sha256=") ? signature.substring(7) : signature;
        byte[] expected = GiteaClient.hmacSha256Hex(secret, payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = normalized.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private boolean matches(String registered, String... candidates) {
        String normalizedRegistered = normalize(registered);
        if (normalizedRegistered == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (normalizedRegistered.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    /** 归一化为 https://host/owner/repo（去掉凭据、.git 与末尾斜杠，兼容 ssh 形式）。 */
    private String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        if (value.startsWith("git@")) {
            int colon = value.indexOf(':');
            if (colon < 0) {
                return null;
            }
            value = "https://" + value.substring(4, colon) + "/" + value.substring(colon + 1);
        }
        String clean = RepoUrls.parse(value).url();
        clean = clean.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return clean.endsWith(".git") ? clean.substring(0, clean.length() - 4) : clean;
    }
}
