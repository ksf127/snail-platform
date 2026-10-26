package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.snail.platform.common.api.BizException;
import com.snail.platform.common.model.Role;
import com.snail.platform.control.dto.MemberBatchRequest;
import com.snail.platform.control.dto.MemberRequest;
import com.snail.platform.control.dto.MemberView;
import com.snail.platform.control.dto.PageView;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ProjectEnvironmentService environmentService;

    public List<ProjectEntity> list() {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .orderByDesc(ProjectEntity::getId));
    }

    public PageView<ProjectEntity> page(int page, int size, String keyword, String status) {
        LambdaQueryWrapper<ProjectEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(ProjectEntity::getProjectKey, value)
                    .or().like(ProjectEntity::getName, value));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProjectEntity::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(ProjectEntity::getId);
        return PageView.of(projectMapper.selectPage(PageView.paging(page, size), wrapper));
    }

    /** 按 Key/名称模糊匹配项目 ID，供发布记录按项目关键字过滤。 */
    public List<Long> idsByKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String value = keyword.trim();
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                        .select(ProjectEntity::getId)
                        .and(w -> w.like(ProjectEntity::getProjectKey, value)
                                .or().like(ProjectEntity::getName, value)))
                .stream().map(ProjectEntity::getId).toList();
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

        // 新项目默认带开发/测试/生产三套环境，namespace 以项目 key 为前缀。
        environmentService.createDefaults(project.getId(), project.getProjectKey());
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

    /** 项目成员分页：关键字匹配用户名/显示名。 */
    public PageView<MemberView> members(Long projectId, int page, int size, String keyword) {
        get(projectId);
        LambdaQueryWrapper<ProjectMemberEntity> wrapper = new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .orderByAsc(ProjectMemberEntity::getId);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                            .select(UserEntity::getId)
                            .and(w -> w.like(UserEntity::getUsername, value)
                                    .or().like(UserEntity::getDisplayName, value)))
                    .stream().map(UserEntity::getId).toList();
            if (matchedUserIds.isEmpty()) {
                return new PageView<>(List.of(), 0, Math.max(1, page),
                        Math.min(100, Math.max(1, size)));
            }
            wrapper.in(ProjectMemberEntity::getUserId, matchedUserIds);
        }
        Page<ProjectMemberEntity> result = memberMapper.selectPage(PageView.paging(page, size), wrapper);
        List<Long> userIds = result.getRecords().stream().map(ProjectMemberEntity::getUserId).toList();
        Map<Long, UserEntity> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return PageView.of(result, m -> {
            UserEntity user = users.get(m.getUserId());
            return new MemberView(m.getId(), m.getUserId(),
                    user == null ? "unknown" : user.getUsername(),
                    user == null ? "unknown" : user.getDisplayName(),
                    m.getRole());
        });
    }

    public MemberView addMember(Long projectId, MemberRequest request) {
        get(projectId);
        UserEntity user = userMapper.selectById(request.userId());
        if (user == null) {
            throw BizException.badRequest("用户不存在: " + request.userId());
        }
        Role role = parseRole(request.role());
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

    /** 批量添加成员：跳过已存在的成员，返回实际新增数量。 */
    @Transactional
    public int addMembers(Long projectId, MemberBatchRequest request) {
        get(projectId);
        Role role = parseRole(request.role());
        List<Long> userIds = request.userIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            throw BizException.badRequest("请选择要添加的用户");
        }
        List<UserEntity> users = userMapper.selectBatchIds(userIds);
        if (users.size() != userIds.size()) {
            throw BizException.badRequest("存在无效的用户 ID");
        }
        Set<Long> existingUserIds = memberMapper.selectList(new LambdaQueryWrapper<ProjectMemberEntity>()
                        .eq(ProjectMemberEntity::getProjectId, projectId)
                        .in(ProjectMemberEntity::getUserId, userIds))
                .stream().map(ProjectMemberEntity::getUserId).collect(Collectors.toSet());
        int added = 0;
        for (Long userId : userIds) {
            if (existingUserIds.contains(userId)) {
                continue;
            }
            ProjectMemberEntity member = new ProjectMemberEntity();
            member.setProjectId(projectId);
            member.setUserId(userId);
            member.setRole(role.name());
            memberMapper.insert(member);
            added++;
        }
        return added;
    }

    /** 批量移除成员：返回实际移除数量。 */
    @Transactional
    public int removeMembers(Long projectId, List<Long> userIds) {
        get(projectId);
        List<Long> targets = userIds == null ? List.of() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (targets.isEmpty()) {
            throw BizException.badRequest("请选择要移除的成员");
        }
        return memberMapper.delete(new LambdaQueryWrapper<ProjectMemberEntity>()
                .eq(ProjectMemberEntity::getProjectId, projectId)
                .in(ProjectMemberEntity::getUserId, targets));
    }

    /** 项目全部成员的 user ID，用于批量添加时标记已加入用户。 */
    public List<Long> memberIds(Long projectId) {
        get(projectId);
        return memberMapper.selectList(new LambdaQueryWrapper<ProjectMemberEntity>()
                        .select(ProjectMemberEntity::getUserId)
                        .eq(ProjectMemberEntity::getProjectId, projectId))
                .stream().map(ProjectMemberEntity::getUserId).toList();
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw BizException.badRequest("非法角色: " + value);
        }
    }

    public boolean isBlank(String value) {
        return !StringUtils.hasText(value);
    }
}
