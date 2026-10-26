package com.snail.platform.control.dto;

import java.time.LocalDateTime;

public record BranchView(
        Long id,
        Long projectId,
        String branch,
        String team,
        Long ownerId,
        String ownerName,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
