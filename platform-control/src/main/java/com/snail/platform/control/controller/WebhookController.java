package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
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

    @PostMapping("/gitea")
    public ApiResult<List<Long>> gitea(@RequestBody String payload,
                                       @RequestHeader(value = "X-Gitea-Signature", required = false) String signature) {
        return ApiResult.ok(giteaWebhookService.handlePush(payload, signature));
    }
}
