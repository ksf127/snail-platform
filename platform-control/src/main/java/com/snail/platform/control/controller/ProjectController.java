package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.MemberRequest;
import com.snail.platform.control.dto.MemberView;
import com.snail.platform.control.dto.ProjectRequest;
import com.snail.platform.control.entity.ProjectEntity;
import com.snail.platform.control.security.AuthUser;
import com.snail.platform.control.service.ProjectService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResult<List<ProjectEntity>> list() {
        return ApiResult.ok(projectService.list());
    }

    @GetMapping("/{id}")
    public ApiResult<ProjectEntity> get(@PathVariable Long id) {
        return ApiResult.ok(projectService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<ProjectEntity> create(@Valid @RequestBody ProjectRequest request,
                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResult.ok(projectService.create(request, user.id()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<ProjectEntity> update(@PathVariable Long id,
                                           @Valid @RequestBody ProjectRequest request) {
        return ApiResult.ok(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.ok();
    }

    @GetMapping("/{id}/members")
    public ApiResult<List<MemberView>> members(@PathVariable Long id) {
        return ApiResult.ok(projectService.members(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<MemberView> addMember(@PathVariable Long id,
                                           @Valid @RequestBody MemberRequest request) {
        return ApiResult.ok(projectService.addMember(id, request));
    }
}
