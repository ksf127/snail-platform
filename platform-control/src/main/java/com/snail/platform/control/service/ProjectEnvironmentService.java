package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.control.dto.EnvironmentRequest;
import com.snail.platform.control.entity.ProjectEnvironmentEntity;
import com.snail.platform.control.mapper.ProjectEnvironmentMapper;
import com.snail.platform.control.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 项目环境配置：每个项目维护自己的环境清单（编码、显示名、namespace、默认副本）。
 * 发布单创建时按环境取 namespace 与副本数，并固化到发布单上。
 */
@Service
@RequiredArgsConstructor
public class ProjectEnvironmentService {

    private static final String[] DEFAULT_ENVS = {"DEV", "TEST", "PROD"};
    private static final String[] DEFAULT_NAMES = {"开发环境", "测试环境", "生产环境"};
    private static final String[] DEFAULT_SUFFIX = {"", "-test", "-prod"};
    private static final int[] DEFAULT_REPLICAS = {1, 1, 2};
    private static final int[] DEFAULT_SORT = {10, 20, 30};

    private final ProjectEnvironmentMapper environmentMapper;
    private final ProjectMapper projectMapper;

    /** 环境清单：projectId 为空时返回全部项目的环境（发布页筛选用）。 */
    public List<ProjectEnvironmentEntity> list(Long projectId, Boolean enabled) {
        return environmentMapper.selectList(new LambdaQueryWrapper<ProjectEnvironmentEntity>()
                .eq(projectId != null, ProjectEnvironmentEntity::getProjectId, projectId)
                .eq(enabled != null, ProjectEnvironmentEntity::getEnabled, enabled)
                .orderByAsc(ProjectEnvironmentEntity::getSortOrder)
                .orderByAsc(ProjectEnvironmentEntity::getId));
    }

    /** 按项目 + 环境编码精确查找；不存在返回 null。 */
    public ProjectEnvironmentEntity find(Long projectId, String name) {
        if (projectId == null || !StringUtils.hasText(name)) {
            return null;
        }
        return environmentMapper.selectOne(new LambdaQueryWrapper<ProjectEnvironmentEntity>()
                .eq(ProjectEnvironmentEntity::getProjectId, projectId)
                .eq(ProjectEnvironmentEntity::getName, name.trim().toUpperCase(Locale.ROOT))
                .last("limit 1"));
    }

    /** 发布时必须命中项目已启用环境，否则明确报错，避免发布到未定义的 namespace。 */
    public ProjectEnvironmentEntity require(Long projectId, String name) {
        ProjectEnvironmentEntity environment = find(projectId, name);
        if (environment == null) {
            throw BizException.badRequest("项目未配置环境: " + name + "，请先在项目详情中维护环境");
        }
        if (!Boolean.TRUE.equals(environment.getEnabled())) {
            throw BizException.badRequest("环境已停用: " + environment.getName());
        }
        return environment;
    }

    /** 新建项目时补齐 DEV / TEST / PROD 三套默认环境，namespace 以项目 key 为前缀。 */
    @Transactional
    public void createDefaults(Long projectId, String projectKey) {
        String prefix = projectKey.toLowerCase(Locale.ROOT);
        for (int i = 0; i < DEFAULT_ENVS.length; i++) {
            if (find(projectId, DEFAULT_ENVS[i]) != null) {
                continue;
            }
            ProjectEnvironmentEntity entity = new ProjectEnvironmentEntity();
            entity.setProjectId(projectId);
            entity.setName(DEFAULT_ENVS[i]);
            entity.setDisplayName(DEFAULT_NAMES[i]);
            entity.setNamespace(prefix + DEFAULT_SUFFIX[i]);
            entity.setDefaultReplicas(DEFAULT_REPLICAS[i]);
            entity.setDescription("默认" + DEFAULT_NAMES[i]);
            entity.setSortOrder(DEFAULT_SORT[i]);
            entity.setEnabled(true);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            environmentMapper.insert(entity);
        }
    }

    public ProjectEnvironmentEntity create(EnvironmentRequest request) {
        requireProject(request.projectId());
        String name = normalizeName(request.name());
        if (find(request.projectId(), name) != null) {
            throw BizException.conflict("环境已存在: " + name);
        }
        ProjectEnvironmentEntity entity = new ProjectEnvironmentEntity();
        entity.setProjectId(request.projectId());
        apply(entity, request, name);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        environmentMapper.insert(entity);
        return entity;
    }

    /** 环境编码是发布单上的历史标识，不允许修改；只更新名称、namespace 与副本等配置。 */
    public ProjectEnvironmentEntity update(Long id, EnvironmentRequest request) {
        ProjectEnvironmentEntity entity = get(id);
        if (!entity.getProjectId().equals(request.projectId())) {
            throw BizException.badRequest("不允许将环境移动到其他项目");
        }
        String name = normalizeName(request.name());
        if (!name.equals(entity.getName())) {
            throw BizException.badRequest("环境编码不允许修改: " + entity.getName());
        }
        apply(entity, request, name);
        entity.setUpdatedAt(LocalDateTime.now());
        environmentMapper.updateById(entity);
        return entity;
    }

    public void delete(Long id) {
        get(id);
        environmentMapper.deleteById(id);
    }

    private void apply(ProjectEnvironmentEntity entity, EnvironmentRequest request, String name) {
        entity.setName(name);
        entity.setDisplayName(request.displayName().trim());
        entity.setNamespace(request.namespace().trim());
        entity.setDefaultReplicas(request.defaultReplicas() == null
                ? 1 : Math.max(1, request.defaultReplicas()));
        entity.setDescription(request.description());
        entity.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private ProjectEnvironmentEntity get(Long id) {
        ProjectEnvironmentEntity entity = environmentMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("环境不存在: " + id);
        }
        return entity;
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw BizException.notFound("项目不存在: " + projectId);
        }
    }

    private String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
