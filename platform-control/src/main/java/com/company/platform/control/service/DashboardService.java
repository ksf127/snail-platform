package com.company.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.platform.common.model.RunStatus;
import com.company.platform.control.dto.DashboardView;
import com.company.platform.control.dto.RunView;
import com.company.platform.control.entity.PipelineRunEntity;
import com.company.platform.control.mapper.PipelineMapper;
import com.company.platform.control.mapper.PipelineRunMapper;
import com.company.platform.control.mapper.ProjectMapper;
import com.company.platform.control.mapper.RepoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjectMapper projectMapper;
    private final RepoMapper repoMapper;
    private final PipelineMapper pipelineMapper;
    private final PipelineRunMapper runMapper;
    private final RunService runService;

    public DashboardView summary() {
        long projects = projectMapper.selectCount(null);
        long repos = repoMapper.selectCount(null);
        long pipelines = pipelineMapper.selectCount(null);
        long runs = runMapper.selectCount(null);
        long success = runMapper.selectCount(new LambdaQueryWrapper<PipelineRunEntity>()
                .eq(PipelineRunEntity::getStatus, RunStatus.SUCCESS.name()));
        long finished = runMapper.selectCount(new LambdaQueryWrapper<PipelineRunEntity>()
                .in(PipelineRunEntity::getStatus, RunStatus.SUCCESS.name(), RunStatus.FAILED.name(),
                        RunStatus.CANCELED.name()));
        double rate = finished == 0 ? 0.0 : Math.round(success * 1000.0 / finished) / 10.0;
        List<RunView> recent = runService.list(null, 10);
        return new DashboardView(projects, repos, pipelines, runs, rate, recent);
    }
}
