package com.snail.platform.control.dto;

import java.time.LocalDateTime;

public record RunView(
        Long id,
        Long pipelineId,
        String pipelineName,
        Integer buildNo,
        String status,
        String commitSha,
        String branch,
        String triggerType,
        String triggeredBy,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String logObject,
        String errorMessage,
        LocalDateTime createdAt
) {
}
