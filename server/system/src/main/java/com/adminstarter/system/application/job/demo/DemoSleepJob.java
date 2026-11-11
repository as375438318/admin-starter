package com.adminstarter.system.application.job.demo;

import com.adminstarter.system.application.job.JobContext;
import com.adminstarter.system.application.job.JobResult;
import com.adminstarter.system.application.job.ScheduledJob;
import org.springframework.stereotype.Component;

/**
 * 演示任务：固定休眠 5 秒。用于验证超时（把任务超时时间设为 1 秒即可观察 TIMEOUT）。
 */
@Component
public class DemoSleepJob implements ScheduledJob {

    @Override
    public String key() {
        return "demo-sleep";
    }

    @Override
    public String description() {
        return "演示：休眠 5 秒，用于验证超时（超时设为 1 秒）";
    }

    @Override
    public JobResult run(JobContext context) throws InterruptedException {
        Thread.sleep(5000L);
        return JobResult.ok(0L);
    }
}
