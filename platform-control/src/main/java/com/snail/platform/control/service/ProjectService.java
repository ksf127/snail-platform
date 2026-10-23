package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.model.Role;
import com.snail.platform.control.dto.MemberRequest;
import com.snail.platform.control.dto.MemberView;
import com.snail.platform.control.dto.ProjectRequest;
import com.snail.platform.control.entity.ProjectEntity;
import com.snail.platform.control.entity.ProjectMemberEntity;
import com.snail.platform.control.entity.UserEntity;
import com.snail.platform.control.mapper.ProjectMapper;
import com.snail.platform.control.mapper.ProjectMemberMapper;
import com.snail.platform.control.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;

    public List<ProjectEntity> list() {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .orderByDesc(ProjectEntity::getId));
    }

    public ProjectEntity get(Long id) {
        ProjectEntity project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.notFound("项目不存在: " + id);
        }
        return project;
    }

    @Transactional
    public ProjectEntity create(ProjectRequest request, Long ownerId) {
        String key = request.key().toUpperCase(Locale.ROOT);
        Long exists = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getProjectKey, key));
        if (exists != null && exists > 0) {
            throw BizException.conflict("项目 key 已存在: " + key);
        }
        ProjectEntity project = new ProjectEntity();
        project.setProjectKey(key);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwnerId(ownerId);
        project.setStatus("ACTIVE");
        project.setCreatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(project.getId());
        member.setUserId(ownerId);
        member.setRole(Role.ADMIN.name());
        memberMapper.insert(member);
        return project;
    }

    public ProjectEntity update(Long id, ProjectRequest request) {
        ProjectEntity project = get(id);
        project.setName(request.name());
        project.setDescription(request.description());
        projectMapper.updateById(project);
        return project;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        projectMapper.deleteById(id);
    }

    public List<MemberView> members(Long projectId) {
        get(projectId);
        List<ProjectMemberEntity> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberEntity>().eq(ProjectMemberEntity::getProjectId, projectId));
        List<Long> userIds = members.stream().map(ProjectMemberEntity::getUserId).toList();
        Map<Long, UserEntity> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return members.stream().map(m -> {
            UserEntity user = users.get(m.getUserId());
            return new MemberView(m.getId(), m.getUserId(),
                    user == null ? "unknown" : user.getUsername(),
                    user == null ? "unknown" : user.getDisplayName(),
                    m.getRole());
        }).toList();
    }

    public MemberView addMember(Long projectId, MemberRequest request) {
        get(projectId);
        UserEntity user = userMapper.selectById(request.userId());
        if (user == null) {
            throw BizException.badRequest("用户不存在: " + request.userId());
        }
        Role role;
        try {
            role = Role.valueOf(request.role().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("非法角色: " + request.role());
        }
        ProjectMemberEntity existing = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .eq(ProjectMemberEntity::getUserId, request.userId()));
        if (existing != null) {
            existing.setRole(role.name());
            memberMapper.updateById(existing);
            return new MemberView(existing.getId(), existing.getUserId(), user.getUsername(),
                    user.getDisplayName(), existing.getRole());
        }
        ProjectMemberEntity member = new ProjectMemberEntity();
        member.setProjectId(projectId);
        member.setUserId(request.userId());
        member.setRole(role.name());
        memberMapper.insert(member);
        return new MemberView(member.getId(), member.getUserId(), user.getUsername(),
                user.getDisplayName(), member.getRole());
    }

    public boolean isBlank(String value) {
        return !StringUtils.hasText(value);
    }
}
