package com.adminstarter.system.application.job.demo;

import com.adminstarter.system.application.job.JobContext;
import com.adminstarter.system.application.job.JobResult;
import com.adminstarter.system.application.job.ScheduledJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示任务：心跳。用于验证“每分钟自动执行 + 日志落库”，不产生业务副作用。
 */
@Slf4j
@Component
public class DemoHeartbeatJob implements ScheduledJob {

    @Override
    public String key() {
        return "demo-heartbeat";
    }

    @Override
    public String description() {
        return "演示：心跳任务（仅写应用日志，返回影响条数 1）";
    }

    @Override
    public JobResult run(JobContext context) {
        log.info("demo-heartbeat 执行：jobId={}, trigger={}", context.jobId(), context.triggerType());
        return JobResult.ok(1L);
    }
}
