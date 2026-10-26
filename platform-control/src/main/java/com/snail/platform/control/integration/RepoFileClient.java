package com.snail.platform.control.integration;

import com.snail.platform.common.api.BizException;
import com.snail.platform.common.build.RepoUrls;
import com.snail.platform.common.model.RepoProvider;
import com.snail.platform.control.entity.RepoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 从仓库读取单个文本文件（当前用于配置即代码的 pipeline.yml）。
 * 支持 GitHub（Contents API）与 Gitea（原生 raw API）；令牌由调用方（service 层）传入。
 */
@Slf4j
@Component
public class RepoFileClient {

    private final RestClient restClient;

    public RepoFileClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public String fetch(RepoEntity repo, String ref, String path, String token) {
        if (repo == null || !StringUtils.hasText(repo.getCloneUrl())) {
            throw BizException.badRequest("仓库地址为空，无法读取流水线配置");
        }
        String cleanPath = normalize(path);
        String branch = StringUtils.hasText(ref) ? ref.trim() : "main";
        String host = RepoUrls.host(repo.getCloneUrl());

        if (host.equalsIgnoreCase("github.com")) {
            return fetchFromGitHub(repo.getCloneUrl(), branch, cleanPath, token);
        }
        if (RepoProvider.GITEA.name().equalsIgnoreCase(repo.getProvider())) {
            return fetchFromGitea(repo.getCloneUrl(), branch, cleanPath, token);
        }
        throw BizException.badRequest("仓库 " + repo.getName()
                + " 不支持从仓库读取配置（仅支持 GitHub 与 Gitea）；请改用平台内步骤定义，或为仓库配置凭据");
    }

    private String fetchFromGitHub(String cloneUrl, String branch, String path, String token) {
        String[] ownerRepo = RepoUrls.ownerAndRepo(cloneUrl);
        try {
            // 统一走 GitHub Contents API：匿名可读公开仓库，令牌可读私有仓库，
            // 且 api.github.com 在受限网络中比 raw.githubusercontent.com 更稳定。
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("api.github.com")
                            .path("/repos/{owner}/{repo}/contents/{path}")
                            .queryParam("ref", branch)
                            .build(ownerRepo[0], ownerRepo[1], path));
            if (StringUtils.hasText(token)) {
                spec = spec.header("Authorization", "Bearer " + token);
            }
            return decode(spec.header("Accept", "application/vnd.github.raw")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(byte[].class));
        } catch (RestClientResponseException ex) {
            throw notFoundOrUnavailable("GitHub", path, branch, ex);
        } catch (ResourceAccessException ex) {
            throw BizException.unavailable("连接 GitHub 失败（" + ex.getMessage()
                    + "），请检查控制面出网或为仓库配置访问令牌");
        }
    }

    private String fetchFromGitea(String cloneUrl, String branch, String path, String token) {
        String[] ownerRepo = RepoUrls.ownerAndRepo(cloneUrl);
        URI base = URI.create(cloneUrl.trim());
        String baseUrl = base.getScheme() + "://" + base.getRawAuthority();
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme(base.getScheme())
                            .host(base.getHost())
                            .port(base.getPort())
                            .path("/api/v1/repos/{owner}/{repo}/raw/{path}")
                            .queryParam("ref", branch)
                            .build(ownerRepo[0], ownerRepo[1], path));
            if (StringUtils.hasText(token)) {
                spec = spec.header("Authorization", "token " + token);
            }
            return decode(spec.retrieve().body(byte[].class));
        } catch (RestClientResponseException ex) {
            log.debug("读取 Gitea 文件失败 base={} path={} branch={}: {}", baseUrl, path, branch, ex.getMessage());
            throw notFoundOrUnavailable("Gitea", path, branch, ex);
        } catch (ResourceAccessException ex) {
            throw BizException.unavailable("连接 Gitea 失败（" + ex.getMessage()
                    + "），请检查控制面到 Gitea 的网络与端口");
        }
    }

    private BizException notFoundOrUnavailable(String provider, String path, String branch,
                                               RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 404) {
            return BizException.badRequest(provider + " 仓库中不存在配置 " + path + "（分支 " + branch
                    + "），请确认路径与分支，或先在仓库中提交该文件");
        }
        if (status == 401 || status == 403) {
            return BizException.badRequest(provider + " 拒绝访问（HTTP " + status
                    + "），请为仓库配置有效的访问令牌");
        }
        return BizException.unavailable("读取 " + provider + " 仓库配置失败: HTTP " + status);
    }

    private String decode(byte[] body) {
        if (body == null || body.length == 0) {
            throw BizException.badRequest("仓库中的配置内容为空");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private String normalize(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isBlank()) {
            throw BizException.badRequest("配置路径不能为空");
        }
        if (value.contains("..")) {
            throw BizException.badRequest("配置路径不能包含 ..");
        }
        return value;
    }
}
