package com.snail.platform.control.dto;

import java.time.LocalDateTime;

/** 项目构建变量视图：只返回掩码，永不返回明文。 */
public record ProjectSecretView(
        Long id,
        Long projectId,
        String name,
        String maskedValue,
        String description,
        LocalDateTime updatedAt
) {
}
