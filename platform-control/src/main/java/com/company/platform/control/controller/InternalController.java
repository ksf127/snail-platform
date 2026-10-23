package com.company.platform.control.controller;

import com.company.platform.common.api.ApiResult;
import com.company.platform.common.build.ArtifactRegistration;
import com.company.platform.common.build.InternalRunContext;
import com.company.platform.common.build.RunStatusUpdate;
import com.company.platform.control.service.InternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * worker 专用接口，由 InternalTokenFilter 校验 X-Internal-Token。
 * 必须在网关屏蔽 /api/internal/**。
 */
@RestController
@RequestMapping("/api/internal/runs")
@RequiredArgsConstructor
public class InternalController {

    private final InternalService internalService;

    @GetMapping("/{id}/context")
    public ApiResult<InternalRunContext> context(@PathVariable Long id) {
        return ApiResult.ok(internalService.context(id));
    }

    @PostMapping("/{id}/status")
    public ApiResult<Void> status(@PathVariable Long id, @RequestBody RunStatusUpdate update) {
        internalService.updateStatus(id, update);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/artifacts")
    public ApiResult<Void> artifact(@PathVariable Long id, @RequestBody ArtifactRegistration registration) {
        internalService.registerArtifact(id, registration);
        return ApiResult.ok();
    }
}
