package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.ProjectSecretRequest;
import com.snail.platform.control.dto.ProjectSecretView;
import com.snail.platform.control.entity.ProjectSecretEntity;
import com.snail.platform.control.service.ProjectSecretService;
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

/** 项目构建变量：接口只返回掩码值，构建时由 control 解密注入容器。 */
@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
public class ProjectSecretController {

    private final ProjectSecretService projectSecretService;

    @GetMapping
    public ApiResult<List<ProjectSecretView>> list(@RequestParam Long projectId) {
        return ApiResult.ok(projectSecretService.list(projectId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<Long> create(@Valid @RequestBody ProjectSecretRequest request) {
        ProjectSecretEntity entity = projectSecretService.create(request);
        return ApiResult.ok(entity.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<Long> update(@PathVariable Long id, @Valid @RequestBody ProjectSecretRequest request) {
        projectSecretService.update(id, request);
        return ApiResult.ok(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectSecretService.delete(id);
        return ApiResult.ok();
    }
}
