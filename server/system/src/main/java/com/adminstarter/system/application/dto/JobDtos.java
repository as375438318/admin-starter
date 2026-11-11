package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

public final class JobDtos {

    private JobDtos() {
    }

    @Data
    public static class Request {
        @NotBlank(message = "任务名称不能为空")
        @Size(max = 64, message = "任务名称不能超过 64 字")
        private String name;

        @Size(max = 64, message = "分组不能超过 64 字")
        private String groupName;

        @NotBlank(message = "任务标识不能为空")
        @Size(max = 128, message = "任务标识不能超过 128 字")
        private String jobKey;

        @NotBlank(message = "cron 表达式不能为空")
        @Size(max = 64, message = "cron 表达式不能超过 64 字")
        private String cronExpression;

        @Size(max = 255, message = "描述不能超过 255 字")
        private String description;

        /** ENABLED / DISABLED，空默认 DISABLED */
        private String status;

        /** DISALLOW / ALLOW，空默认 DISALLOW */
        private String concurrencyPolicy;

        @Min(value = 1, message = "超时时间至少 1 秒")
        @Max(value = 3600, message = "超时时间不能超过 3600 秒")
        private Integer timeoutSeconds;

        @Min(value = 0, message = "重试次数不能为负")
        @Max(value = 10, message = "重试次数不能超过 10")
        private Integer retryTimes;

        @Min(value = 0, message = "重试间隔不能为负")
        @Max(value = 3600, message = "重试间隔不能超过 3600 秒")
        private Integer retryIntervalSeconds;

        /** NEVER / ON_FAILURE / ON_CONSECUTIVE，空默认 NEVER */
        private String notifyPolicy;

        @Min(value = 1, message = "连续失败阈值至少 1")
        @Max(value = 10, message = "连续失败阈值不能超过 10")
        private Integer notifyThreshold;

        @Size(max = 255, message = "通知接收人不能超过 255 字")
        private String notifyTargets;
    }

    @Data
    public static class StatusRequest {
        @NotBlank(message = "状态不能为空")
        private String status;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String groupName;
        private String jobKey;
        private String cronExpression;
        private String description;
        private String status;
        private String concurrencyPolicy;
        private Integer timeoutSeconds;
        private Integer retryTimes;
        private Integer retryIntervalSeconds;
        private String notifyPolicy;
        private Integer notifyThreshold;
        private String notifyTargets;
        private Instant lastRunAt;
        private String lastRunResult;
        private Instant nextRunAt;
        private Boolean running;
        private Boolean registered;
        private Long createdBy;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class RunResponse {
        private Long logId;
        private String result;
        private Long elapsedMs;
        private Integer attemptCount;
        private Long affectedRows;
        private String errorSummary;
        private Instant startedAt;
        private Instant finishedAt;
    }

    public record JobKeyOption(String key, String description) {
    }
}
