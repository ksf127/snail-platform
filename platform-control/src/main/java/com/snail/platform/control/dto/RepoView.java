package com.snail.platform.control.dto;

public record RepoView(
        Long id,
        Long projectId,
        String name,
        String provider,
        String externalId,
        String cloneUrl,
        String defaultBranch,
        boolean webhookConfigured,
        boolean credentialConfigured
) {
}
