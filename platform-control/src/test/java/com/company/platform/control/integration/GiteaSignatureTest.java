package com.company.platform.control.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GiteaSignatureTest {

    private static final String SECRET = "webhook-secret";
    private static final String PAYLOAD = "{\"ref\":\"refs/heads/main\",\"after\":\"abc123\"}";

    @Test
    void acceptsValidSignature() {
        String signature = GiteaClient.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(GiteaClient.verifySignature(SECRET, PAYLOAD, signature)).isTrue();
    }

    @Test
    void acceptsSignatureWithPrefixAndUppercase() {
        String signature = GiteaClient.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(GiteaClient.verifySignature(SECRET, PAYLOAD, "sha256=" + signature)).isTrue();
        assertThat(GiteaClient.verifySignature(SECRET, PAYLOAD, signature.toUpperCase())).isTrue();
    }

    @Test
    void rejectsTamperedPayload() {
        String signature = GiteaClient.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(GiteaClient.verifySignature(SECRET, PAYLOAD + " ", signature)).isFalse();
    }

    @Test
    void rejectsWrongSecretAndMissingValues() {
        String signature = GiteaClient.hmacSha256Hex(SECRET, PAYLOAD);

        assertThat(GiteaClient.verifySignature("other-secret", PAYLOAD, signature)).isFalse();
        assertThat(GiteaClient.verifySignature(null, PAYLOAD, signature)).isFalse();
        assertThat(GiteaClient.verifySignature(SECRET, PAYLOAD, null)).isFalse();
        assertThat(GiteaClient.verifySignature("", PAYLOAD, signature)).isFalse();
    }
}
