package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.control.dto.ProjectSecretRequest;
import com.snail.platform.control.dto.ProjectSecretView;
import com.snail.platform.control.entity.ProjectSecretEntity;
import com.snail.platform.control.mapper.ProjectSecretMapper;
import com.snail.platform.control.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 项目级构建变量：DB 存密文，接口只暴露掩码，构建时解密注入容器。 */
@Service
@RequiredArgsConstructor
public class ProjectSecretService {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final ProjectSecretMapper secretMapper;
    private final ProjectService projectService;
    private final SecretCipher secretCipher;

    public List<ProjectSecretView> list(Long projectId) {
        projectService.get(projectId);
        return secretMapper.selectList(new LambdaQueryWrapper<ProjectSecretEntity>()
                        .eq(ProjectSecretEntity::getProjectId, projectId)
                        .orderByAsc(ProjectSecretEntity::getName))
                .stream().map(this::toView).toList();
    }

    public ProjectSecretEntity create(ProjectSecretRequest request) {
        projectService.get(request.projectId());
        String name = requireName(request.name());
        if (!StringUtils.hasText(request.value())) {
            throw BizException.badRequest("变量值不能为空");
        }
        Long exists = secretMapper.selectCount(new LambdaQueryWrapper<ProjectSecretEntity>()
                .eq(ProjectSecretEntity::getProjectId, request.projectId())
                .eq(ProjectSecretEntity::getName, name));
        if (exists != null && exists > 0) {
            throw BizException.conflict("变量已存在: " + name + "，请直接编辑该变量");
        }
        ProjectSecretEntity entity = new ProjectSecretEntity();
        entity.setProjectId(request.projectId());
        entity.setName(name);
        entity.setValueCipher(secretCipher.encrypt(request.value()));
        entity.setDescription(request.description());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        secretMapper.insert(entity);
        return entity;
    }

    public ProjectSecretEntity update(Long id, ProjectSecretRequest request) {
        ProjectSecretEntity entity = require(id);
        if (!entity.getProjectId().equals(request.projectId())) {
            throw BizException.badRequest("不允许把变量移动到其他项目");
        }
        String name = requireName(request.name());
        Long duplicated = secretMapper.selectCount(new LambdaQueryWrapper<ProjectSecretEntity>()
                .eq(ProjectSecretEntity::getProjectId, request.projectId())
                .eq(ProjectSecretEntity::getName, name)
                .ne(ProjectSecretEntity::getId, id));
        if (duplicated != null && duplicated > 0) {
            throw BizException.conflict("变量名已被使用: " + name);
        }
        entity.setName(name);
        if (StringUtils.hasText(request.value())) {
            entity.setValueCipher(secretCipher.encrypt(request.value()));
        }
        entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now());
        secretMapper.updateById(entity);
        return entity;
    }

    public void delete(Long id) {
        require(id);
        secretMapper.deleteById(id);
    }

    /** 构建上下文：解密为环境变量，键名已校验，可直接用于容器 env。 */
    public Map<String, String> envOf(Long projectId) {
        if (projectId == null) {
            return Map.of();
        }
        Map<String, String> env = new LinkedHashMap<>();
        for (ProjectSecretEntity entity : secretMapper.selectList(new LambdaQueryWrapper<ProjectSecretEntity>()
                .eq(ProjectSecretEntity::getProjectId, projectId)
                .orderByAsc(ProjectSecretEntity::getName))) {
            env.put(entity.getName(), secretCipher.decrypt(entity.getValueCipher()));
        }
        return env;
    }

    private ProjectSecretEntity require(Long id) {
        ProjectSecretEntity entity = secretMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("构建变量不存在: " + id);
        }
        return entity;
    }

    private String requireName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw BizException.badRequest("变量名需以字母或下划线开头，且只包含字母、数字、下划线: " + raw);
        }
        return name;
    }

    private ProjectSecretView toView(ProjectSecretEntity entity) {
        String plain = secretCipher.decrypt(entity.getValueCipher());
        return new ProjectSecretView(entity.getId(), entity.getProjectId(), entity.getName(),
                SecretCipher.mask(plain), entity.getDescription(), entity.getUpdatedAt());
    }
}
