package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.DeploymentExecutionView;
import com.snail.platform.control.dto.DeploymentRequest;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.entity.DeploymentEntity;
import com.snail.platform.control.service.DeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @GetMapping
    public ApiResult<PageView<DeploymentEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String projectKeyword,
                                                      @RequestParam(required = false) String environment,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResult.ok(deploymentService.page(page, size, projectId, projectKeyword, environment,
                status, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<DeploymentEntity> create(@Valid @RequestBody DeploymentRequest request) {
        return ApiResult.ok(deploymentService.create(request));
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<DeploymentExecutionView> execute(@PathVariable Long id) {
        return ApiResult.ok(deploymentService.execute(id));
    }
}
