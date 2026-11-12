package com.adminstarter.system.application;

import com.adminstarter.system.application.dto.DashboardDtos;
import com.adminstarter.system.infrastructure.persistence.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计聚合。
 *
 * <p>口径全部为<b>系统级聚合</b>（不随数据权限收窄），由权限码 {@code system:dashboard:view} 把关。</p>
 *
 * <p>趋势按 UTC 自然日（库内时间统一存 UTC），区间固定近 7 天，缺数据的日子补 0 —— 前端直接画柱状图，
 * 不需要再处理"某些天没有数据"的情况。</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardAppService {

    private static final int TREND_DAYS = 7;

    private final DashboardMapper dashboardMapper;

    public DashboardDtos.Overview overview() {
        DashboardDtos.Counts counts = new DashboardDtos.Counts(
                dashboardMapper.countUsers(),
                dashboardMapper.countEnabledUsers(),
                dashboardMapper.countRoles(),
                dashboardMapper.countDepts(),
                dashboardMapper.countPosts(),
                dashboardMapper.countDictTypes(),
                dashboardMapper.countDictItems(),
                dashboardMapper.countConfigs(),
                dashboardMapper.countNotices(),
                dashboardMapper.countJobs(),
                dashboardMapper.countEnabledJobs(),
                dashboardMapper.countActiveSessions());
        DashboardDtos.Storage storage = new DashboardDtos.Storage(
                dashboardMapper.countFiles(), dashboardMapper.sumFileBytes());
        DashboardDtos.Today today = new DashboardDtos.Today(
                dashboardMapper.countTodayLoginSuccess(),
                dashboardMapper.countTodayLoginFailure(),
                dashboardMapper.countTodayOperations());
        return new DashboardDtos.Overview(counts, storage, today, trends(), taskStatus());
    }

    private List<DashboardDtos.TrendPoint> trends() {
        Map<String, Long> logins = toDayMap(dashboardMapper.countLoginsByDay(TREND_DAYS));
        Map<String, Long> operations = toDayMap(dashboardMapper.countOperationsByDay(TREND_DAYS));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<DashboardDtos.TrendPoint> points = new ArrayList<>(TREND_DAYS);
        // 从最早到最近，前端按数组顺序渲染
        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            String day = today.minusDays(offset).toString();
            points.add(new DashboardDtos.TrendPoint(day, logins.getOrDefault(day, 0L),
                    operations.getOrDefault(day, 0L)));
        }
        return points;
    }

    private Map<String, Long> toDayMap(List<Map<String, Object>> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            Object total = row.get("total");
            if (day != null && total instanceof Number number) {
                result.put(day.toString(), number.longValue());
            }
        }
        return result;
    }

    private List<DashboardDtos.TaskStatusCount> taskStatus() {
        List<DashboardDtos.TaskStatusCount> result = new ArrayList<>();
        for (Map<String, Object> row : dashboardMapper.countDemoTasksByStatus()) {
            Object status = row.get("status");
            Object total = row.get("total");
            if (status != null && total instanceof Number number) {
                result.add(new DashboardDtos.TaskStatusCount(status.toString(), number.longValue()));
            }
        }
        return result;
    }
}