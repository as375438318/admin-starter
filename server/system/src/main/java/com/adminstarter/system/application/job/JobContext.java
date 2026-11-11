package com.adminstarter.system.application.job;

/**
 * 任务执行上下文：只读信息，不含任何可执行字符串。
 */
public record JobContext(Long jobId, String jobKey, String jobName, String triggerType) {
}
