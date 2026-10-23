package com.snail.platform.worker.client;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.common.build.ArtifactRegistration;
import com.snail.platform.common.build.InternalRunContext;
import com.snail.platform.common.build.RunStatusUpdate;
import com.snail.platform.common.internal.InternalHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * worker 到 control 的内部调用。所有请求都带 X-Internal-Token。
 */
@Component
public class ControlClient {

    private static final ParameterizedTypeReference<ApiResult<InternalRunContext>> CONTEXT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public ControlClient(RestClient.Builder builder,
                         @Value("${platform.control.url:http://localhost:8088}") String baseUrl,
                         @Value("${platform.internal.token}") String internalToken) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.INTERNAL_TOKEN, internalToken)
                .build();
    }

    public InternalRunContext fetchContext(Long runId) {
        try {
            ApiResult<InternalRunContext> result = restClient.get()
                    .uri("/api/internal/runs/{id}/context", runId)
                    .retrieve()
                    .body(CONTEXT_TYPE);
            return unwrap(result, "拉取运行上下文");
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("拉取运行上下文失败: HTTP " + ex.getStatusCode().value()
                    + " " + ex.getResponseBodyAsString(), ex);
        }
    }

    public void updateStatus(Long runId, RunStatusUpdate update) {
        try {
            restClient.post()
                    .uri("/api/internal/runs/{id}/status", runId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(update)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("回报运行状态失败: HTTP " + ex.getStatusCode().value()
                    + " " + ex.getResponseBodyAsString(), ex);
        }
    }

    public void registerArtifact(Long runId, ArtifactRegistration registration) {
        try {
            restClient.post()
                    .uri("/api/internal/runs/{id}/artifacts", runId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(registration)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("登记制品失败: HTTP " + ex.getStatusCode().value()
                    + " " + ex.getResponseBodyAsString(), ex);
        }
    }

    private <T> T unwrap(ApiResult<T> result, String action) {
        if (result == null) {
            throw new IllegalStateException(action + "失败: 控制面返回空响应");
        }
        if (result.code() != 0) {
            throw new IllegalStateException(action + "失败: " + result.message());
        }
        return result.data();
    }
}
