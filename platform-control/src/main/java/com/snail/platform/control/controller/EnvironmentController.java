package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.EnvironmentRequest;
import com.snail.platform.control.entity.ProjectEnvironmentEntity;
import com.snail.platform.control.service.ProjectEnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** 项目环境配置：开发、测试、生产等环境的 namespace 与默认副本。 */
@RestController
@RequestMapping("/api/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final ProjectEnvironmentService environmentService;

    @GetMapping
    public ApiResult<List<ProjectEnvironmentEntity>> list(@RequestParam(required = false) Long projectId,
                                                          @RequestParam(required = false) Boolean enabled) {
        return ApiResult.ok(environmentService.list(projectId, enabled));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<ProjectEnvironmentEntity> create(@Valid @RequestBody EnvironmentRequest request) {
        return ApiResult.ok(environmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<ProjectEnvironmentEntity> update(@PathVariable Long id,
                                                      @Valid @RequestBody EnvironmentRequest request) {
        return ApiResult.ok(environmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        environmentService.delete(id);
        return ApiResult.ok();
    }
}
