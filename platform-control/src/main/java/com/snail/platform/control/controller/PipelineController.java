package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.common.model.TriggerType;
import com.snail.platform.control.dto.MonorepoPipelineRequest;
import com.snail.platform.control.dto.PipelineRequest;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.dto.PipelineTemplateView;
import com.snail.platform.control.dto.RunView;
import com.snail.platform.control.dto.TriggerRequest;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.PipelineRunEntity;
import com.snail.platform.control.security.AuthUser;
import com.snail.platform.control.service.PipelineService;
import com.snail.platform.control.service.PipelineTemplateService;
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
    private final PipelineTemplateService pipelineTemplateService;
    private final RunService runService;

    /**
     * 生成步骤模板：stack 为空或 AUTO 时按仓库自动识别，name 为空时按仓库名给出建议名称。
     * side=FRONTEND 时使用前端 Node 模板并把 workDir 指向前端子目录 frontendDir；
     * side=BACKEND 时 workDir 固定为仓库根目录。
     */
    @GetMapping("/templates/suggest")
    public ApiResult<PipelineTemplateView> template(@RequestParam(required = false) Long repoId,
                                                    @RequestParam(required = false) String stack,
                                                    @RequestParam(required = false) String name,
                                                    @RequestParam(required = false) String side,
                                                    @RequestParam(required = false) String frontendDir) {
        return ApiResult.ok(pipelineTemplateService.suggest(repoId, stack, name, side, frontendDir));
    }

    /** 单仓（前后端同一个仓库）一键生成前端、后端两条流水线。 */
    @PostMapping("/templates/monorepo")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ApiResult<List<PipelineEntity>> createMonorepo(
            @Valid @RequestBody MonorepoPipelineRequest request) {
        return ApiResult.ok(pipelineService.createMonorepo(request));
    }

    @GetMapping
    public ApiResult<PageView<PipelineEntity>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) Long repoId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String triggerType,
                                                    @RequestParam(required = false) String executorType,
                                                    @RequestParam(required = false) Boolean enabled,
                                                    @RequestParam(required = false) String side,
                                                    @RequestParam(required = false) String branch,
                                                    @RequestParam(required = false) String team) {
        return ApiResult.ok(pipelineService.page(page, size, projectId, repoId, keyword,
                triggerType, executorType, enabled, side, branch, team));
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
