package com.company.platform.control.controller;

import com.company.platform.common.api.ApiResult;
import com.company.platform.control.dto.DashboardView;
import com.company.platform.control.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ApiResult<DashboardView> summary() {
        return ApiResult.ok(dashboardService.summary());
    }
}
