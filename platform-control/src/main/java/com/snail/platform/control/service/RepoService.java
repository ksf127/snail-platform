package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.model.RepoProvider;
import com.snail.platform.control.dto.BindRepoRequest;
import com.snail.platform.control.dto.CreateRepoRequest;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.dto.RepoCreatedView;
import com.snail.platform.control.dto.RepoView;
import com.snail.platform.control.entity.RepoEntity;
import com.snail.platform.control.integration.GiteaClient;
import com.snail.platform.control.mapper.RepoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepoService {

    private final RepoMapper repoMapper;
    private final ProjectService projectService;
    private final GiteaClient giteaClient;

    public List<RepoView> list(Long projectId) {
        LambdaQueryWrapper<RepoEntity> wrapper = new LambdaQueryWrapper<RepoEntity>()
                .orderByDesc(RepoEntity::getId);
        if (projectId != null) {
            wrapper.eq(RepoEntity::getProjectId, projectId);
        }
        return repoMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    public PageView<RepoView> page(int page, int size, Long projectId, String keyword, String provider) {
        LambdaQueryWrapper<RepoEntity> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(RepoEntity::getProjectId, projectId);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(RepoEntity::getName, value)
                    .or().like(RepoEntity::getCloneUrl, value));
        }
        if (StringUtils.hasText(provider)) {
            wrapper.eq(RepoEntity::getProvider, provider.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(RepoEntity::getId);
        return PageView.of(repoMapper.selectPage(PageView.paging(page, size), wrapper), this::toView);
    }

    public RepoEntity get(Long id) {
        RepoEntity repo = repoMapper.selectById(id);
        if (repo == null) {
            throw BizException.notFound("仓库不存在: " + id);
        }
        return repo;
    }

    /** 在 Gitea 中创建仓库并纳入平台管理。Gitea 未配置时明确失败，提示改用手工绑定。 */
    public RepoCreatedView createInGitea(CreateRepoRequest request) {
        projectService.get(request.projectId());
        GiteaClient.GiteaRepo remote = giteaClient.createRepo(
                request.name(),
                request.description(),
                Boolean.TRUE.equals(request.privateRepo()),
                request.defaultBranch());

        RepoEntity repo = new RepoEntity();
        repo.setProjectId(request.projectId());
        repo.setName(remote.name());
        repo.setProvider(RepoProvider.GITEA.name());
        repo.setExternalId(String.valueOf(remote.id()));
        repo.setCloneUrl(remote.cloneUrl());
        repo.setDefaultBranch(remote.defaultBranch() == null ? "main" : remote.defaultBranch());
        repo.setWebhookSecret(secret(request.webhookSecret()));
        repo.setCreatedAt(LocalDateTime.now());
        repoMapper.insert(repo);
        return new RepoCreatedView(repo, repo.getWebhookSecret());
    }

    /** 不依赖 Gitea 的手工绑定（GENERIC），保证 MVP 在无 Gitea 环境可运行。 */
    public RepoCreatedView bindGeneric(BindRepoRequest request) {
        projectService.get(request.projectId());
        RepoEntity repo = new RepoEntity();
        repo.setProjectId(request.projectId());
        repo.setName(request.name());
        repo.setProvider(RepoProvider.GENERIC.name());
        repo.setExternalId(null);
        repo.setCloneUrl(request.cloneUrl());
        repo.setDefaultBranch(StringUtils.hasText(request.defaultBranch()) ? request.defaultBranch() : "main");
        repo.setWebhookSecret(secret(request.webhookSecret()));
        repo.setCreatedAt(LocalDateTime.now());
        repoMapper.insert(repo);
        return new RepoCreatedView(repo, repo.getWebhookSecret());
    }

    public void delete(Long id) {
        get(id);
        repoMapper.deleteById(id);
    }

    public List<RepoEntity> findByGiteaIdentity(long externalId, String cloneUrl, String name) {
        LambdaQueryWrapper<RepoEntity> wrapper = new LambdaQueryWrapper<RepoEntity>()
                .eq(RepoEntity::getProvider, RepoProvider.GITEA.name())
                .and(w -> {
                    w.eq(RepoEntity::getExternalId, String.valueOf(externalId));
                    if (StringUtils.hasText(cloneUrl)) {
                        w.or().eq(RepoEntity::getCloneUrl, cloneUrl);
                    }
                    if (StringUtils.hasText(name)) {
                        w.or().eq(RepoEntity::getName, name);
                    }
                });
        return repoMapper.selectList(wrapper);
    }

    /** 当前环境是否配置了 Gitea（未配置时只能手工绑定已有仓库）。 */
    public boolean giteaConfigured() {
        return giteaClient.isConfigured();
    }

    private RepoView toView(RepoEntity repo) {
        return new RepoView(repo.getId(), repo.getProjectId(), repo.getName(), repo.getProvider(),
                repo.getExternalId(), repo.getCloneUrl(), repo.getDefaultBranch(),
                StringUtils.hasText(repo.getWebhookSecret()));
    }

    private String secret(String requested) {
        return StringUtils.hasText(requested) ? requested : UUID.randomUUID().toString().replace("-", "");
    }
}
