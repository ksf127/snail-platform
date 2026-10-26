package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.build.BuildMessage;
import com.snail.platform.common.build.PipelineDefinition;
import com.snail.platform.common.model.ExecutorType;
import com.snail.platform.common.model.PipelineSide;
import com.snail.platform.common.model.RunStatus;
import com.snail.platform.common.model.TriggerType;
import com.snail.platform.control.dto.MonorepoPipelineRequest;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.dto.PipelineRequest;
import com.snail.platform.control.dto.PipelineTemplateView;
import com.snail.platform.control.dto.PipelineYamlView;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.PipelineRunEntity;
import com.snail.platform.control.entity.ProjectBranchEntity;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.BuildMessagePublisher;
import com.snail.platform.control.mapper.PipelineMapper;
import com.snail.platform.control.mapper.PipelineRunMapper;
import com.snail.platform.control.mapper.RepoMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineMapper pipelineMapper;
    private final PipelineRunMapper pipelineRunMapper;
    private final RepoMapper repoMapper;
    private final ProjectService projectService;
    private final ProjectBranchService projectBranchService;
    private final PipelineTemplateService pipelineTemplateService;
    private final PipelineConfigService pipelineConfigService;
    private final BuildMessagePublisher messagePublisher;
    private final ObjectMapper objectMapper;

    @Value("${platform.executor.default:DOCKER}")
    private String defaultExecutor;

    /** 同一流水线允许排队的任务上限，避免重复触发堆积。 */
    @Value("${platform.build.max-queued-per-pipeline:3}")
    private int maxQueuedPerPipeline;

    public List<PipelineEntity> list(Long projectId) {
        LambdaQueryWrapper<PipelineEntity> wrapper = new LambdaQueryWrapper<PipelineEntity>()
                .orderByDesc(PipelineEntity::getId);
        if (projectId != null) {
            wrapper.eq(PipelineEntity::getProjectId, projectId);
        }
        List<PipelineEntity> rows = pipelineMapper.selectList(wrapper);
        fillTeams(rows);
        return rows;
    }

    public PageView<PipelineEntity> page(int page, int size, Long projectId, Long repoId,
                                         String keyword, String triggerType, String executorType,
                                         Boolean enabled, String side, String branch, String team) {
        LambdaQueryWrapper<PipelineEntity> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(PipelineEntity::getProjectId, projectId);
        }
        if (repoId != null) {
            wrapper.eq(PipelineEntity::getRepoId, repoId);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            List<Long> matchedProjectIds = projectService.idsByKeyword(value);
            wrapper.and(w -> {
                w.like(PipelineEntity::getName, value);
                if (!matchedProjectIds.isEmpty()) {
                    w.or().in(PipelineEntity::getProjectId, matchedProjectIds);
                }
            });
        }
        if (StringUtils.hasText(triggerType)) {
            wrapper.eq(PipelineEntity::getTriggerType, triggerType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(executorType)) {
            wrapper.eq(PipelineEntity::getExecutorType, executorType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(side)) {
            wrapper.eq(PipelineEntity::getSide, side.trim().toUpperCase(Locale.ROOT));
        }
        if (enabled != null) {
            wrapper.eq(PipelineEntity::getEnabled, enabled);
        }
        if (StringUtils.hasText(branch)) {
            wrapper.eq(PipelineEntity::getBranch, branch.trim());
        }
        if (StringUtils.hasText(team)) {
            List<ProjectBranchEntity> owned = projectBranchService.listByTeam(projectId, team);
            if (owned.isEmpty()) {
                return new PageView<>(List.of(), 0, Math.max(1, page),
                        Math.min(100, Math.max(1, size)));
            }
            wrapper.and(w -> {
                for (int i = 0; i < owned.size(); i++) {
                    ProjectBranchEntity row = owned.get(i);
                    if (i == 0) {
                        w.eq(PipelineEntity::getProjectId, row.getProjectId())
                                .eq(PipelineEntity::getBranch, row.getBranch());
                    } else {
                        w.or(o -> o.eq(PipelineEntity::getProjectId, row.getProjectId())
                                .eq(PipelineEntity::getBranch, row.getBranch()));
                    }
                }
            });
        }
        wrapper.orderByDesc(PipelineEntity::getId);
        PageView<PipelineEntity> view = PageView.of(pipelineMapper.selectPage(PageView.paging(page, size), wrapper));
        fillTeams(view.items());
        return view;
    }

    /** 回填分支归属团队，便于列表按团队识别维护方。 */
    private void fillTeams(List<PipelineEntity> pipelines) {
        if (pipelines == null || pipelines.isEmpty()) {
            return;
        }
        List<Long> projectIds = pipelines.stream().map(PipelineEntity::getProjectId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        var teams = projectBranchService.teamMap(projectIds);
        pipelines.forEach(p -> {
            var branchTeams = teams.get(p.getProjectId());
            p.setTeam(branchTeams == null ? null : branchTeams.get(p.getBranch()));
        });
    }

    public PipelineEntity get(Long id) {
        PipelineEntity pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw BizException.notFound("流水线不存在: " + id);
        }
        return pipeline;
    }

    public PipelineEntity create(PipelineRequest request) {
        projectService.get(request.projectId());
        PipelineDefinition definition = validate(request);
        validateRepo(request.projectId(), request.repoId());

        PipelineEntity pipeline = new PipelineEntity();
        apply(pipeline, request, definition);
        pipeline.setCreatedAt(LocalDateTime.now());
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.insert(pipeline);
        projectBranchService.ensure(pipeline.getProjectId(), pipeline.getBranch());
        return pipeline;
    }

    /**
     * 单仓（前后端同一个仓库）一键生成两条流水线：
     * 前端在子目录（默认 web）构建，后端在仓库根目录构建，各自独立触发与发布。
     * 已存在对应端流水线时跳过，两端都已存在则直接报错，避免重复生成。
     */
    @Transactional
    public List<PipelineEntity> createMonorepo(MonorepoPipelineRequest request) {
        projectService.get(request.projectId());
        RepoEntity repo = request.repoId() == null ? null : repoMapper.selectById(request.repoId());
        if (repo == null) {
            throw BizException.badRequest("请选择关联仓库");
        }
        if (!repo.getProjectId().equals(request.projectId())) {
            throw BizException.badRequest("仓库不属于该项目");
        }
        List<PipelineEntity> existing = pipelineMapper.selectList(new LambdaQueryWrapper<PipelineEntity>()
                .eq(PipelineEntity::getRepoId, repo.getId()));
        boolean hasFrontend = existing.stream().anyMatch(p -> PipelineSide.FRONTEND.name().equals(p.getSide()));
        boolean hasBackend = existing.stream().anyMatch(p -> PipelineSide.BACKEND.name().equals(p.getSide()));
        if (hasFrontend && hasBackend) {
            throw BizException.badRequest("仓库 " + repo.getName() + " 已有前后端流水线，请直接编辑或删除后重试");
        }
        String branch = StringUtils.hasText(request.branch()) ? request.branch().trim()
                : (StringUtils.hasText(repo.getDefaultBranch()) ? repo.getDefaultBranch() : "main");
        String triggerType = StringUtils.hasText(request.triggerType())
                ? request.triggerType() : TriggerType.MANUAL.name();

        List<PipelineEntity> created = new ArrayList<>();
        for (PipelineTemplateView view
                : pipelineTemplateService.suggestMonorepo(repo, request.frontendDir(), request.backendStack())) {
            if ((PipelineSide.FRONTEND.name().equals(view.side()) && hasFrontend)
                    || (PipelineSide.BACKEND.name().equals(view.side()) && hasBackend)) {
                continue;
            }
            created.add(create(new PipelineRequest(request.projectId(), repo.getId(), view.name(), branch,
                    triggerType, view.executorType(), view.side(), view.definitionJson(), true, null)));
        }
        return created;
    }

    public PipelineEntity update(Long id, PipelineRequest request) {
        PipelineEntity pipeline = get(id);
        if (!pipeline.getProjectId().equals(request.projectId())) {
            throw BizException.badRequest("不允许将流水线移动到其他项目");
        }
        PipelineDefinition definition = validate(request);
        validateRepo(request.projectId(), request.repoId());
        apply(pipeline, request, definition);
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
        projectBranchService.ensure(pipeline.getProjectId(), pipeline.getBranch());
        return pipeline;
    }

    public void delete(Long id) {
        get(id);
        pipelineMapper.deleteById(id);
    }

    /** 导出当前步骤定义为 YAML。 */
    public PipelineYamlView yaml(Long id) {
        PipelineEntity pipeline = get(id);
        PipelineDefinition definition = pipelineConfigService.readJson(pipeline.getDefinitionJson());
        return new PipelineYamlView(pipelineConfigService.toYaml(definition),
                pipelineConfigService.writeJson(definition), pipeline.getConfigPath(), "平台定义");
    }

    /** 导入 YAML：解析校验后覆盖平台内步骤定义（configPath 非空时仍以仓库配置优先）。 */
    public PipelineYamlView saveYaml(Long id, String yaml) {
        PipelineEntity pipeline = get(id);
        PipelineDefinition definition = pipelineConfigService.parseYaml(yaml);
        pipeline.setDefinitionJson(pipelineConfigService.writeJson(definition));
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
        return new PipelineYamlView(yaml, pipeline.getDefinitionJson(), pipeline.getConfigPath(), "平台定义");
    }

    /** 从仓库读取 YAML（ref 为空用流水线分支），校验通过后写入本地定义作为预览。 */
    public PipelineYamlView syncYaml(Long id, String ref) {
        PipelineEntity pipeline = get(id);
        if (!StringUtils.hasText(pipeline.getConfigPath())) {
            throw BizException.badRequest("该流水线未配置仓库内配置路径，请先保存配置路径再同步");
        }
        String targetRef = StringUtils.hasText(ref) ? ref.trim() : pipeline.getBranch();
        PipelineConfigService.Resolved resolved = pipelineConfigService.resolve(pipeline, targetRef, null);
        pipeline.setDefinitionJson(resolved.json());
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(pipeline);
        return new PipelineYamlView(pipelineConfigService.toYaml(resolved.definition()),
                resolved.json(), pipeline.getConfigPath(), resolved.source());
    }

    /**
     * 触发一次构建：先校验配置与排队上限，再落 run（QUEUED），最后投递 MQ。
     * 触发前解析一次定义（配置在仓库时会读取远端 YAML），解析结果写入 run 快照，
     * worker 只按快照执行，避免构建期间配置被改动。
     */
    public PipelineRunEntity trigger(Long pipelineId, TriggerType triggerType, String branch,
                                     String commitSha, String triggeredBy) {
        PipelineEntity pipeline = get(pipelineId);
        if (!Boolean.TRUE.equals(pipeline.getEnabled())) {
            throw BizException.badRequest("流水线已禁用，无法触发");
        }
        RepoEntity repo = pipeline.getRepoId() == null ? null : repoMapper.selectById(pipeline.getRepoId());
        if (repo == null || !StringUtils.hasText(repo.getCloneUrl())) {
            throw BizException.badRequest("流水线未绑定可用仓库（cloneUrl 为空）");
        }
        String targetBranch = StringUtils.hasText(branch) ? branch : pipeline.getBranch();
        Long queued = pipelineRunMapper.selectCount(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getPipelineId, pipelineId)
                .eq(PipelineRunEntity::getStatus, RunStatus.QUEUED.name()));
        if (queued != null && queued >= maxQueuedPerPipeline) {
            throw BizException.conflict("该流水线排队任务已达上限 " + maxQueuedPerPipeline
                    + "，请等待执行完成或取消排队任务");
        }
        PipelineConfigService.Resolved resolved =
                pipelineConfigService.resolve(pipeline, targetBranch, commitSha);

        PipelineRunEntity run = new PipelineRunEntity();
        run.setPipelineId(pipelineId);
        run.setBuildNo(nextBuildNo(pipelineId));
        run.setStatus(RunStatus.QUEUED.name());
        run.setBranch(targetBranch);
        run.setCommitSha(commitSha);
        run.setResolvedDefinition(resolved.json());
        run.setTriggerType(triggerType.name());
        run.setTriggeredBy(triggeredBy);
        run.setCreatedAt(LocalDateTime.now());
        pipelineRunMapper.insert(run);

        try {
            messagePublisher.publish(new BuildMessage(run.getId(), pipelineId));
        } catch (RuntimeException ex) {
            run.setStatus(RunStatus.FAILED.name());
            run.setErrorMessage("投递构建消息失败: " + ex.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            pipelineRunMapper.updateById(run);
            throw BizException.unavailable("构建任务入队失败: " + ex.getMessage());
        }
        return run;
    }

    /** Webhook push 事件：命中同仓库、同分支（支持 *）、启用且触发方式为 WEBHOOK 的流水线。 */
    public List<Long> triggerWebhook(RepoEntity repo, String branch, String commitSha, String triggeredBy) {
        List<PipelineEntity> pipelines = pipelineMapper.selectList(new LambdaQueryWrapper<PipelineEntity>()
                .eq(PipelineEntity::getRepoId, repo.getId())
                .eq(PipelineEntity::getEnabled, true)
                .eq(PipelineEntity::getTriggerType, TriggerType.WEBHOOK.name()));
        return pipelines.stream()
                .filter(p -> "*".equals(p.getBranch()) || p.getBranch().equals(branch))
                .filter(p -> !alreadyBuilt(p.getId(), branch, commitSha))
                .map(p -> trigger(p.getId(), TriggerType.WEBHOOK, branch, commitSha, triggeredBy).getId())
                .toList();
    }

    /** 同一提交已排队/执行/成功时跳过，避免 webhook 重投或重复推送导致重复构建。 */
    private boolean alreadyBuilt(Long pipelineId, String branch, String commitSha) {
        if (!StringUtils.hasText(commitSha)) {
            return false;
        }
        Long exists = pipelineRunMapper.selectCount(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getPipelineId, pipelineId)
                .eq(PipelineRunEntity::getBranch, branch)
                .eq(PipelineRunEntity::getCommitSha, commitSha)
                .in(PipelineRunEntity::getStatus, List.of(RunStatus.QUEUED.name(),
                        RunStatus.RUNNING.name(), RunStatus.SUCCESS.name())));
        return exists != null && exists > 0;
    }

    private int nextBuildNo(Long pipelineId) {
        PipelineRunEntity last = pipelineRunMapper.selectOne(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getPipelineId, pipelineId)
                .orderByDesc(PipelineRunEntity::getBuildNo)
                .last("limit 1"));
        return last == null || last.getBuildNo() == null ? 1 : last.getBuildNo() + 1;
    }

    private void apply(PipelineEntity pipeline, PipelineRequest request, PipelineDefinition definition) {
        pipeline.setProjectId(request.projectId());
        pipeline.setRepoId(request.repoId());
        pipeline.setName(request.name());
        pipeline.setBranch(request.branch());
        pipeline.setTriggerType(parseEnum(TriggerType.class, request.triggerType(), "触发方式").name());
        String executor = StringUtils.hasText(request.executorType())
                ? request.executorType() : defaultExecutor;
        pipeline.setExecutorType(parseEnum(ExecutorType.class, executor, "执行器类型").name());
        pipeline.setSide(resolveSide(request));
        pipeline.setDefinitionJson(write(definition));
        pipeline.setConfigPath(pipelineConfigService.normalizeConfigPath(request.configPath()));
        pipeline.setEnabled(request.enabled() == null || request.enabled());
    }

    /** 未显式指定端时按步骤镜像推断：node/npm/yarn/pnpm/vite 视为前端。 */
    private String resolveSide(PipelineRequest request) {
        if (StringUtils.hasText(request.side())) {
            return parseEnum(PipelineSide.class, request.side(), "所属端").name();
        }
        String json = request.definitionJson() == null ? "" : request.definitionJson().toLowerCase(Locale.ROOT);
        boolean frontend = json.contains("node:") || json.contains("npm ")
                || json.contains("yarn ") || json.contains("pnpm ") || json.contains("vite");
        return frontend ? PipelineSide.FRONTEND.name() : PipelineSide.BACKEND.name();
    }

    private void validateRepo(Long projectId, Long repoId) {
        if (repoId == null) {
            return;
        }
        RepoEntity repo = repoMapper.selectById(repoId);
        if (repo == null) {
            throw BizException.badRequest("仓库不存在: " + repoId);
        }
        if (!repo.getProjectId().equals(projectId)) {
            throw BizException.badRequest("仓库不属于该项目");
        }
    }

    private PipelineDefinition validate(PipelineRequest request) {
        parseEnum(TriggerType.class, request.triggerType(), "触发方式");
        parseEnum(ExecutorType.class, request.executorType() == null ? defaultExecutor : request.executorType(),
                "执行器类型");
        String configPath = pipelineConfigService.normalizeConfigPath(request.configPath());
        if (!StringUtils.hasText(request.definitionJson()) && configPath == null) {
            throw BizException.badRequest("请提供流水线步骤定义，或填写仓库内配置路径（如 .snail/pipeline.yml）");
        }
        if (!StringUtils.hasText(request.definitionJson())) {
            // 配置在仓库：步骤在触发时读取并校验，这里先允许空定义。
            return new PipelineDefinition();
        }
        PipelineDefinition definition;
        try {
            definition = objectMapper.readValue(request.definitionJson(), PipelineDefinition.class);
        } catch (JsonProcessingException ex) {
            throw BizException.badRequest("流水线定义不是合法 JSON: " + ex.getOriginalMessage());
        }
        return pipelineConfigService.normalize(definition);
    }

    private String write(PipelineDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException ex) {
            throw BizException.badRequest("流水线定义序列化失败");
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw BizException.badRequest("非法" + label + ": " + value);
        }
    }
}
