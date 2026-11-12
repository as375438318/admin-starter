package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.system.application.DashboardAppService;
import com.adminstarter.system.application.dto.DashboardDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘统计：只读聚合，需要 {@code system:dashboard:view}。
 *
 * <p>没有这个权限码时返回 403（不是空数据）：让"看不到"和"没有数据"在前端可区分。</p>
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardAppService dashboardAppService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('system:dashboard:view')")
    public ApiResponse<DashboardDtos.Overview> overview() {
        return ApiResponse.ok(dashboardAppService.overview());
    }
}