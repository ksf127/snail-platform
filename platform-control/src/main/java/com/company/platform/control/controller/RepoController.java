package com.company.platform.control.controller;

import com.company.platform.common.api.ApiResult;
import com.company.platform.control.dto.BindRepoRequest;
import com.company.platform.control.dto.CreateRepoRequest;
import com.company.platform.control.dto.RepoCreatedView;
import com.company.platform.control.dto.RepoView;
import com.company.platform.control.service.RepoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;

    @GetMapping
    public ApiResult<List<RepoView>> list(@RequestParam(required = false) Long projectId) {
        return ApiResult.ok(repoService.list(projectId));
    }

    /** 通过 Gitea 创建仓库。Gitea 未配置时返回 503，请改用 /bind。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<RepoCreatedView> create(@Valid @RequestBody CreateRepoRequest request) {
        return ApiResult.ok(repoService.createInGitea(request));
    }

    /** 手工绑定已有 cloneUrl（GENERIC），不依赖 Gitea。 */
    @PostMapping("/bind")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<RepoCreatedView> bind(@Valid @RequestBody BindRepoRequest request) {
        return ApiResult.ok(repoService.bindGeneric(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        repoService.delete(id);
        return ApiResult.ok();
    }
}
