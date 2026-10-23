package com.company.platform.control.dto;

import java.time.LocalDateTime;

public record ArtifactView(
        Long id,
        Long runId,
        String fileName,
        String objectKey,
        Long size,
        String sha256,
        String contentType,
        LocalDateTime createdAt
) {
}
