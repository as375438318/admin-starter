package com.adminstarter.system.application.job.demo;

import com.adminstarter.system.application.job.JobContext;
import com.adminstarter.system.application.job.JobResult;
import com.adminstarter.system.application.job.ScheduledJob;
import org.springframework.stereotype.Component;

/**
 * 演示任务：固定失败。用于验证失败日志、重试与失败通知。
 */
@Component
public class DemoFailJob implements ScheduledJob {

    @Override
    public String key() {
        return "demo-fail";
    }

    @Override
    public String description() {
        return "演示：固定抛异常，用于验证失败日志、重试与站内信通知";
    }

    @Override
    public JobResult run(JobContext context) {
        throw new IllegalStateException("演示任务异常：模拟业务失败（jobId=" + context.jobId() + "）");
    }
}
