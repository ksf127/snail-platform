package com.company.platform.control.dto;

import java.util.List;

public record DashboardView(
        long projectCount,
        long repoCount,
        long pipelineCount,
        long runCount,
        double successRate,
        List<RunView> recentRuns
) {
}
