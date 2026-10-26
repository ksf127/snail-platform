package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;

/** 仓库访问凭据：token 可为 GitHub PAT / Gitea token。 */
public record RepoCredentialRequest(
        String username,
        @NotBlank(message = "令牌不能为空") String token
) {
}
