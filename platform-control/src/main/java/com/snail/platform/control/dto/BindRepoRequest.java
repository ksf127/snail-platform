package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BindRepoRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotBlank(message = "仓库名不能为空") String name,
        @NotBlank(message = "cloneUrl 不能为空") String cloneUrl,
        String defaultBranch,
        String webhookSecret,
        /** 私有仓库用户名，可空。 */
        String username,
        /** 私有仓库令牌，落库前加密，接口不回显。 */
        String token
) {
}
