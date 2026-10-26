package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.BindRepoRequest;
import com.snail.platform.control.dto.CreateRepoRequest;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.dto.RepoCreatedView;
import com.snail.platform.control.dto.RepoCredentialRequest;
import com.snail.platform.control.dto.RepoView;
import com.snail.platform.control.service.RepoService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class RepoController {

    private final RepoService repoService;

    @GetMapping
    public ApiResult<PageView<RepoView>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String provider) {
        return ApiResult.ok(repoService.page(page, size, projectId, keyword, provider));
    }

    /** 仓库能力：前端据此决定是否展示「Gitea 创建」。 */
    @GetMapping("/capabilities")
    public ApiResult<Map<String, Boolean>> capabilities() {
        return ApiResult.ok(Map.of("giteaConfigured", repoService.giteaConfigured()));
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

    /** 保存/覆盖仓库访问凭据（GitHub PAT / Gitea token），仅加密入库，不回显。 */
    @PutMapping("/{id}/credential")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<RepoView> saveCredential(@PathVariable Long id,
                                              @Valid @RequestBody RepoCredentialRequest request) {
        return ApiResult.ok(repoService.saveCredential(id, request.username(), request.token()));
    }

    @DeleteMapping("/{id}/credential")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<Void> deleteCredential(@PathVariable Long id) {
        repoService.deleteCredential(id);
        return ApiResult.ok();
    }

    /** 查看 webhook 密钥，用于在 GitHub/Gitea 侧配置 webhook。 */
    @GetMapping("/{id}/webhook-secret")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<Map<String, String>> webhookSecret(@PathVariable Long id) {
        return ApiResult.ok(Map.of("secret", repoService.webhookSecret(id)));
    }
}
