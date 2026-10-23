package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberRequest(
        @NotNull(message = "用户 ID 不能为空") Long userId,
        @NotBlank(message = "角色不能为空") String role
) {
}
