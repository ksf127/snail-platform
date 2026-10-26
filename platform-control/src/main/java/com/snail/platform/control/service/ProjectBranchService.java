package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.control.dto.BranchRequest;
import com.snail.platform.control.dto.BranchView;
import com.snail.platform.control.entity.PipelineEntity;
import com.snail.platform.control.entity.ProjectBranchEntity;
import com.snail.platform.control.entity.UserEntity;
import com.snail.platform.control.mapper.PipelineMapper;
import com.snail.platform.control.mapper.ProjectBranchMapper;
import com.snail.platform.control.mapper.ProjectMapper;
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

/** 项目分支归属：登记一个项目有哪些分支、每个分支由哪个团队/负责人维护。 */
@Service
@RequiredArgsConstructor
public class ProjectBranchService {

    private final ProjectBranchMapper branchMapper;
    private final ProjectMapper projectMapper;
    private final PipelineMapper pipelineMapper;
    private final UserMapper userMapper;

    public List<BranchView> list(Long projectId, String team, String keyword) {
        LambdaQueryWrapper<ProjectBranchEntity> wrapper = new LambdaQueryWrapper<ProjectBranchEntity>()
                .orderByAsc(ProjectBranchEntity::getBranch);
        if (projectId != null) {
            wrapper.eq(ProjectBranchEntity::getProjectId, projectId);
        }
        if (StringUtils.hasText(team)) {
            wrapper.eq(ProjectBranchEntity::getTeam, team.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(ProjectBranchEntity::getBranch, value)
                    .or().like(ProjectBranchEntity::getTeam, value));
        }
        List<ProjectBranchEntity> rows = branchMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ownerIds = rows.stream().map(ProjectBranchEntity::getOwnerId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, UserEntity> owners = ownerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return rows.stream()
                .map(row -> toView(row, row.getOwnerId() == null ? null : owners.get(row.getOwnerId())))
                .toList();
    }

    /** 按团队取「项目 + 分支」归属行；projectId 为空时跨项目返回。 */
    public List<ProjectBranchEntity> listByTeam(Long projectId, String team) {
        LambdaQueryWrapper<ProjectBranchEntity> wrapper = new LambdaQueryWrapper<ProjectBranchEntity>()
                .select(ProjectBranchEntity::getProjectId, ProjectBranchEntity::getBranch)
                .eq(ProjectBranchEntity::getTeam, team.trim());
        if (projectId != null) {
            wrapper.eq(ProjectBranchEntity::getProjectId, projectId);
        }
        return branchMapper.selectList(wrapper);
    }

    /** 批量取「项目 → 分支 → 团队」映射，供流水线列表回填团队列。 */
    public Map<Long, Map<String, String>> teamMap(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        return branchMapper.selectList(new LambdaQueryWrapper<ProjectBranchEntity>()
                        .in(ProjectBranchEntity::getProjectId, projectIds))
                .stream()
                .filter(row -> StringUtils.hasText(row.getTeam()))
                .collect(Collectors.groupingBy(ProjectBranchEntity::getProjectId,
                        Collectors.toMap(ProjectBranchEntity::getBranch, ProjectBranchEntity::getTeam,
                                (a, b) -> a)));
    }

    /** 流水线创建/修改时自动登记分支，保证分支清单完整；通配符 * 不登记。 */
    public void ensure(Long projectId, String branch) {
        if (!StringUtils.hasText(branch) || "*".equals(branch.trim())) {
            return;
        }
        String value = branch.trim();
        Long exists = branchMapper.selectCount(new LambdaQueryWrapper<ProjectBranchEntity>()
                .eq(ProjectBranchEntity::getProjectId, projectId)
                .eq(ProjectBranchEntity::getBranch, value));
        if (exists != null && exists > 0) {
            return;
        }
        ProjectBranchEntity entity = new ProjectBranchEntity();
        entity.setProjectId(projectId);
        entity.setBranch(value);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        branchMapper.insert(entity);
    }

    public BranchView create(BranchRequest request) {
        requireProject(request.projectId());
        String branch = request.branch().trim();
        if ("*".equals(branch)) {
            throw BizException.badRequest("通配符 * 不能登记为具体分支");
        }
        Long exists = branchMapper.selectCount(new LambdaQueryWrapper<ProjectBranchEntity>()
                .eq(ProjectBranchEntity::getProjectId, request.projectId())
                .eq(ProjectBranchEntity::getBranch, branch));
        if (exists != null && exists > 0) {
            throw BizException.conflict("分支已存在: " + branch);
        }
        ProjectBranchEntity entity = new ProjectBranchEntity();
        entity.setProjectId(request.projectId());
        entity.setBranch(branch);
        entity.setTeam(trimToNull(request.team()));
        entity.setOwnerId(requireOwner(request.ownerId()));
        entity.setDescription(trimToNull(request.description()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        branchMapper.insert(entity);
        return toView(entity, entity.getOwnerId() == null ? null : userMapper.selectById(entity.getOwnerId()));
    }

    /** 修改分支归属；分支名变更时同步更新该项目下流水线的分支，避免归属丢失。 */
    @Transactional
    public BranchView update(Long id, BranchRequest request) {
        ProjectBranchEntity entity = get(id);
        if (!entity.getProjectId().equals(request.projectId())) {
            throw BizException.badRequest("不允许将分支移动到其他项目");
        }
        String branch = request.branch().trim();
        if ("*".equals(branch)) {
            throw BizException.badRequest("通配符 * 不能登记为具体分支");
        }
        if (!branch.equals(entity.getBranch())) {
            Long exists = branchMapper.selectCount(new LambdaQueryWrapper<ProjectBranchEntity>()
                    .eq(ProjectBranchEntity::getProjectId, entity.getProjectId())
                    .eq(ProjectBranchEntity::getBranch, branch)
                    .ne(ProjectBranchEntity::getId, id));
            if (exists != null && exists > 0) {
                throw BizException.conflict("分支已存在: " + branch);
            }
            String oldBranch = entity.getBranch();
            pipelineMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PipelineEntity>()
                    .eq(PipelineEntity::getProjectId, entity.getProjectId())
                    .eq(PipelineEntity::getBranch, oldBranch)
                    .set(PipelineEntity::getBranch, branch)
                    .set(PipelineEntity::getUpdatedAt, LocalDateTime.now()));
            entity.setBranch(branch);
        }
        entity.setTeam(trimToNull(request.team()));
        entity.setOwnerId(requireOwner(request.ownerId()));
        entity.setDescription(trimToNull(request.description()));
        entity.setUpdatedAt(LocalDateTime.now());
        branchMapper.updateById(entity);
        return toView(entity, entity.getOwnerId() == null ? null : userMapper.selectById(entity.getOwnerId()));
    }

    public void delete(Long id) {
        get(id);
        branchMapper.deleteById(id);
    }

    private ProjectBranchEntity get(Long id) {
        ProjectBranchEntity entity = branchMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("分支记录不存在: " + id);
        }
        return entity;
    }

    private void requireProject(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw BizException.notFound("项目不存在: " + projectId);
        }
    }

    private Long requireOwner(Long ownerId) {
        if (ownerId == null) {
            return null;
        }
        if (userMapper.selectById(ownerId) == null) {
            throw BizException.badRequest("负责人不存在: " + ownerId);
        }
        return ownerId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BranchView toView(ProjectBranchEntity entity, UserEntity owner) {
        return new BranchView(entity.getId(), entity.getProjectId(), entity.getBranch(), entity.getTeam(),
                entity.getOwnerId(),
                owner == null ? null : owner.getDisplayName() + "(" + owner.getUsername() + ")",
                entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /** 团队名去空白后统一大小写不敏感比较，避免「Web组 / web组」重复。 */
    public String normalizeTeam(String team) {
        return StringUtils.hasText(team) ? team.trim().toLowerCase(Locale.ROOT) : null;
    }
}
