package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class JobDO extends BaseDO {

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
}
