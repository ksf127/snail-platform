package com.company.platform.control.controller;

import com.company.platform.common.api.ApiResult;
import com.company.platform.control.dto.ArtifactView;
import com.company.platform.control.dto.RunView;
import com.company.platform.control.service.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RunController {

    private final RunService runService;

    @GetMapping("/runs")
    public ApiResult<List<RunView>> list(@RequestParam(required = false) Long pipelineId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(runService.list(pipelineId, limit));
    }

    @GetMapping("/runs/{id}")
    public ApiResult<RunView> get(@PathVariable Long id) {
        return ApiResult.ok(runService.get(id));
    }

    @PostMapping("/runs/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<RunView> cancel(@PathVariable Long id) {
        return ApiResult.ok(runService.cancel(id));
    }

    /** 返回 1 小时有效的日志预签名地址。 */
    @GetMapping("/runs/{id}/log")
    public ApiResult<Map<String, String>> log(@PathVariable Long id) {
        return ApiResult.ok(Map.of("url", runService.logUrl(id)));
    }

    @GetMapping("/runs/{id}/artifacts")
    public ApiResult<List<ArtifactView>> artifacts(@PathVariable Long id) {
        return ApiResult.ok(runService.artifacts(id));
    }

    @GetMapping("/artifacts/{id}/download")
    public ApiResult<Map<String, String>> download(@PathVariable Long id) {
        return ApiResult.ok(Map.of("url", runService.artifactUrl(id)));
    }
}
