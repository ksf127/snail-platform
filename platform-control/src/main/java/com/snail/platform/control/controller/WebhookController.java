package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.service.GitHubWebhookService;
import com.snail.platform.control.service.GiteaWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final GiteaWebhookService giteaWebhookService;
    private final GitHubWebhookService gitHubWebhookService;

    @PostMapping("/gitea")
    public ApiResult<List<Long>> gitea(@RequestBody String payload,
                                       @RequestHeader(value = "X-Gitea-Signature", required = false) String signature) {
        return ApiResult.ok(giteaWebhookService.handlePush(payload, signature));
    }

    /** GitHub push webhook：仓库 Settings → Webhooks 中配置，Content-Type 选 application/json。 */
    @PostMapping("/github")
    public ApiResult<List<Long>> github(@RequestBody String payload,
                                        @RequestHeader(value = "X-GitHub-Event", required = false) String event,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        return ApiResult.ok(gitHubWebhookService.handlePush(payload, event, signature));
    }
}
