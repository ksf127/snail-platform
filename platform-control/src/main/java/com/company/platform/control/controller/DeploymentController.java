package com.company.platform.control.controller;

import com.company.platform.common.api.ApiResult;
import com.company.platform.control.dto.DeploymentExecutionView;
import com.company.platform.control.dto.DeploymentRequest;
import com.company.platform.control.entity.DeploymentEntity;
import com.company.platform.control.service.DeploymentService;
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
    public ApiResult<List<DeploymentEntity>> list(@RequestParam(required = false) Long projectId) {
        return ApiResult.ok(deploymentService.list(projectId));
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
