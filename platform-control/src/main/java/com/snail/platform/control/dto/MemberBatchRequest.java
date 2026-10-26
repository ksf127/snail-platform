package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MemberBatchRequest(
        @NotEmpty(message = "用户 ID 不能为空") List<Long> userIds,
        @NotBlank(message = "角色不能为空") String role
) {
}
