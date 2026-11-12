package com.adminstarter.system.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 仪表盘统计响应体。
 *
 * <p><b>口径</b>：全部为<b>系统级聚合</b>，不随数据权限收窄（不区分部门/归属）。因此接口由
 * {@code system:dashboard:view} 权限码保护，默认只授予 SUPER_ADMIN。若后续把该权限授予部门管理员，
 * 会看到全系统聚合数——这是已知取舍，见 CR-20261112-031。</p>
 *
 * <p>只返回计数与按天趋势，不返回任何明细、账号、IP 或文件内容。</p>
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Overview {
        private Counts counts;
        private Storage storage;
        private Today today;
        private List<TrendPoint> trends;
        private List<TaskStatusCount> taskStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Counts {
        private long users;
        private long enabledUsers;
        private long roles;
        private long depts;
        private long posts;
        private long dictTypes;
        private long dictItems;
        private long configs;
        private long notices;
        private long jobs;
        private long enabledJobs;
        private long activeSessions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Storage {
        private long files;
        private long totalBytes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Today {
        private long loginSuccess;
        private long loginFailure;
        private long operations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        /** yyyy-MM-dd（服务器时区的自然日，与趋势区间一致） */
        private String date;
        private long logins;
        private long operations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatusCount {
        private String status;
        private long count;
    }
}