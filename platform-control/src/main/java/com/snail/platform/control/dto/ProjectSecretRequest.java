package com.snail.platform.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 新建/更新项目构建变量。value 为空表示保持不变（更新场景）。 */
public record ProjectSecretRequest(
        @NotNull(message = "项目 ID 不能为空") Long projectId,
        @NotBlank(message = "变量名不能为空") String name,
        String value,
        String description
) {
}
