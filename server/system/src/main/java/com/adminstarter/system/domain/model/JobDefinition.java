package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务定义（领域模型）。只存“已注册任务标识 + 调度配置”，不存任何可执行字符串。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobDefinition extends BaseModel {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

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
    private LocalDateTime lastRunAt;
    private String lastRunResult;
    private LocalDateTime nextRunAt;
    private Long createBy;

    public boolean enabled() {
        return STATUS_ENABLED.equals(status);
    }
}
