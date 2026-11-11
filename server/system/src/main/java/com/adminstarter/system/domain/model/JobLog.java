package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志（审计数据，不做软删除）。
 */
@Data
public class JobLog {

    public static final String TRIGGER_AUTO = "AUTO";
    public static final String TRIGGER_MANUAL = "MANUAL";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_TIMEOUT = "TIMEOUT";
    public static final String RESULT_SKIPPED = "SKIPPED";

    private Long id;
    private Long jobId;
    private String jobName;
    private String jobKey;
    private String triggerType;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String result;
    private Integer attemptCount;
    private String errorSummary;
    private String errorStack;
    private Long affectedRows;
    private LocalDateTime createdAt;
}
