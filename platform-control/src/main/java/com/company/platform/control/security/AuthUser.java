package com.company.platform.control.security;

/** 当前登录用户，写入 SecurityContext 的 principal。 */
public record AuthUser(Long id, String username, String displayName, String role) {
}
