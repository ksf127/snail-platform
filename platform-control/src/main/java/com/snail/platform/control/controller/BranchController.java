package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.BranchRequest;
import com.snail.platform.control.dto.BranchView;
import com.snail.platform.control.service.ProjectBranchService;
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

/** 项目分支归属：多分支项目可按团队维护各分支负责人。 */
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final ProjectBranchService projectBranchService;

    @GetMapping
    public ApiResult<List<BranchView>> list(@RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String team,
                                            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(projectBranchService.list(projectId, team, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<BranchView> create(@Valid @RequestBody BranchRequest request) {
        return ApiResult.ok(projectBranchService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<BranchView> update(@PathVariable Long id,
                                        @Valid @RequestBody BranchRequest request) {
        return ApiResult.ok(projectBranchService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectBranchService.delete(id);
        return ApiResult.ok();
    }
}
