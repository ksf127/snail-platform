package com.snail.platform.common.build;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 仓库地址工具：脱敏展示、注入凭据、拆出用户名密码。
 * 凭据只用于拼接 git 命令，任何日志都必须先经过 {@link #mask(String)}。
 */
public final class RepoUrls {

    private RepoUrls() {
    }

    /** 把 https://user:token@host/repo.git 变成 https://***@host/repo.git，并屏蔽常见 token 参数。 */
    public static String mask(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String masked = url.replaceAll("(?i)(://)[^/@\\s]+@", "$1***@");
        return masked.replaceAll("(?i)([?&](?:token|access_token|private_token)=)[^&]+", "$1***");
    }

    /** 私有仓库 clone 用：把凭据注入 HTTP(S) 地址，非 http 协议原样返回。 */
    public static String withCredentials(String url, String username, String token) {
        if (url == null || url.isBlank() || token == null || token.isBlank()
                || !url.regionMatches(true, 0, "http", 0, 4)) {
            return url;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url;
        }
        String prefix = url.substring(0, schemeEnd + 3);
        String rest = url.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        String hostPart = slash < 0 ? rest : rest.substring(0, slash);
        String tail = slash < 0 ? "" : rest.substring(slash);
        String user = username == null || username.isBlank() ? "git" : username.trim();
        return prefix + encode(user) + ":" + encode(token.trim()) + "@" + hostPart + tail;
    }

    /** 从地址中拆出 userinfo，返回纯净地址与凭据（用于绑定时把明文 token 收进凭据表）。 */
    public static Parsed parse(String url) {
        if (url == null || url.isBlank()) {
            return new Parsed(url, null, null);
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return new Parsed(url, null, null);
        }
        String prefix = url.substring(0, schemeEnd + 3);
        String rest = url.substring(schemeEnd + 3);
        int at = rest.indexOf('@');
        int slash = rest.indexOf('/');
        if (at < 0 || (slash >= 0 && at > slash)) {
            return new Parsed(url, null, null);
        }
        String userInfo = rest.substring(0, at);
        String clean = prefix + rest.substring(at + 1);
        int colon = userInfo.indexOf(':');
        String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
        String pass = colon < 0 ? null : userInfo.substring(colon + 1);
        return new Parsed(clean, user, pass);
    }

    /** 解析 owner/repo，用于 GitHub Raw / API 地址拼接。 */
    public static String[] ownerAndRepo(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("仓库地址为空");
        }
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            String[] parts = path.split("/");
            if (parts.length < 2) {
                throw new IllegalArgumentException("无法从地址解析 owner/repo: " + mask(url));
            }
            return new String[]{parts[0], parts[1]};
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("无法从地址解析 owner/repo: " + mask(url), ex);
        }
    }

    /** 地址所属主机，例如 github.com / gitea.example.com:3000。 */
    public static String host(String url) {
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record Parsed(String url, String username, String password) {
    }
}
