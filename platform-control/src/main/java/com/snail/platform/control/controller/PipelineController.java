package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.common.model.TriggerType;
import com.snail.platform.control.dto.PipelineRequest;
import com.snail.platform.control.dto.RunView;
import com.snail.platform.control.dto.TriggerRequest;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.PipelineRunEntity;
import com.snail.platform.control.security.AuthUser;
import com.snail.platform.control.service.PipelineService;
import com.snail.platform.control.service.RunService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final RunService runService;

    @GetMapping
    public ApiResult<List<PipelineEntity>> list(@RequestParam(required = false) Long projectId) {
        return ApiResult.ok(pipelineService.list(projectId));
    }

    @GetMapping("/{id}")
    public ApiResult<PipelineEntity> get(@PathVariable Long id) {
        return ApiResult.ok(pipelineService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<PipelineEntity> create(@Valid @RequestBody PipelineRequest request) {
        return ApiResult.ok(pipelineService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<PipelineEntity> update(@PathVariable Long id,
                                            @Valid @RequestBody PipelineRequest request) {
        return ApiResult.ok(pipelineService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        pipelineService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/trigger")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<PipelineRunEntity> trigger(@PathVariable Long id,
                                                @RequestBody(required = false) TriggerRequest request,
                                                @AuthenticationPrincipal AuthUser user) {
        String branch = request == null ? null : request.branch();
        String sha = request == null ? null : request.commitSha();
        return ApiResult.ok(pipelineService.trigger(id, TriggerType.MANUAL, branch, sha, user.username()));
    }

    @GetMapping("/{id}/runs")
    public ApiResult<List<RunView>> runs(@PathVariable Long id,
                                         @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(runService.list(id, limit));
    }
}
