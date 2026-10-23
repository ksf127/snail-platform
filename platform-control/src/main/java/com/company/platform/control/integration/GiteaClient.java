package com.company.platform.control.integration;

import com.company.platform.common.api.BizException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Gitea REST 客户端。仅在 platform.gitea.url 与 token 都配置时可用。
 * 未配置时平台仍可运行，仓库通过 GENERIC 方式手工绑定 cloneUrl。
 */
@Component
public class GiteaClient {

    private final String baseUrl;
    private final String token;

    public GiteaClient(@Value("${platform.gitea.url:}") String baseUrl,
                       @Value("${platform.gitea.token:}") String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.token = token == null ? "" : token.trim();
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank() && !token.isBlank();
    }

    public GiteaRepo createRepo(String name, String description, boolean privateRepo, String defaultBranch) {
        requireConfigured();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("private", privateRepo);
        body.put("auto_init", true);
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        if (defaultBranch != null && !defaultBranch.isBlank()) {
            body.put("default_branch", defaultBranch);
        }
        try {
            return client().post()
                    .uri("/api/v1/user/repos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GiteaRepo.class);
        } catch (RestClientResponseException ex) {
            throw BizException.unavailable("Gitea 创建仓库失败: HTTP " + ex.getStatusCode().value()
                    + " " + abbreviate(ex.getResponseBodyAsString()));
        }
    }

    public List<GiteaRepo> listRepos() {
        requireConfigured();
        try {
            GiteaRepo[] repos = client().get()
                    .uri("/api/v1/user/repos?limit=50")
                    .retrieve()
                    .body(GiteaRepo[].class);
            return repos == null ? List.of() : List.of(repos);
        } catch (RestClientResponseException ex) {
            throw BizException.unavailable("Gitea 查询仓库失败: HTTP " + ex.getStatusCode().value());
        }
    }

    public GiteaRepo getRepo(String owner, String name) {
        requireConfigured();
        try {
            return client().get()
                    .uri("/api/v1/repos/{owner}/{name}", owner, name)
                    .retrieve()
                    .body(GiteaRepo.class);
        } catch (RestClientResponseException ex) {
            throw BizException.unavailable("Gitea 查询仓库失败: HTTP " + ex.getStatusCode().value());
        }
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "token " + token)
                .build();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw BizException.unavailable("Gitea 未配置（platform.gitea.url / token），请使用手工绑定 cloneUrl 的方式创建 GENERIC 仓库");
        }
    }

    /** 校验 X-Gitea-Signature：HMAC-SHA256(payload, secret) 的十六进制，常量时间比较。 */
    public static boolean verifySignature(String secret, String payload, String signature) {
        if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        String normalized = signature.startsWith("sha256=") ? signature.substring(7) : signature;
        byte[] expected = hmacSha256Hex(secret, payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = normalized.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", ex);
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GiteaRepo(
            long id,
            String name,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("clone_url") String cloneUrl,
            @JsonProperty("default_branch") String defaultBranch
    ) {
    }
}
