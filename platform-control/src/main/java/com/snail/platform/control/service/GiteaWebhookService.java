package com.snail.platform.control.service;

import com.snail.platform.common.api.BizException;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.GiteaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GiteaWebhookService {

    private final RepoService repoService;
    private final PipelineService pipelineService;
    private final ObjectMapper objectMapper;

    /**
     * 验签通过后才解析事件，签名对每个候选仓库的 secret 逐个校验。
     * 返回本次触发的 runId 列表。
     */
    public List<Long> handlePush(String payload, String signature) {
        if (payload == null || payload.isBlank()) {
            throw BizException.badRequest("空的 webhook 请求体");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw BizException.badRequest("webhook 请求体不是合法 JSON");
        }
        JsonNode repository = root.path("repository");
        long externalId = repository.path("id").asLong();
        String cloneUrl = repository.path("clone_url").asText(null);
        String name = repository.path("name").asText(null);

        List<RepoEntity> candidates = repoService.findByGiteaIdentity(externalId, cloneUrl, name);
        RepoEntity repo = candidates.stream()
                .filter(r -> GiteaClient.verifySignature(r.getWebhookSecret(), payload, signature))
                .findFirst()
                .orElseThrow(() -> BizException.unauthorized("webhook 签名校验失败或仓库未登记"));

        String ref = root.path("ref").asText("");
        String branch = ref.startsWith("refs/heads/")
                ? ref.substring("refs/heads/".length())
                : root.path("branch").asText(repo.getDefaultBranch() == null ? "main" : repo.getDefaultBranch());
        String sha = root.path("after").asText(null);
        if (sha == null || sha.isBlank()) {
            sha = root.path("head_commit").path("id").asText(null);
        }
        return pipelineService.triggerWebhook(repo, branch, sha, "gitea-webhook");
    }
}
