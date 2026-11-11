package com.adminstarter.system.application.export;

import com.adminstarter.system.application.job.JobContext;
import com.adminstarter.system.application.job.JobResult;
import com.adminstarter.system.application.job.ScheduledJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 导出临时文件过期清理：每小时执行一次，标记 EXPIRED 并删除文件。
 */
@Component
@RequiredArgsConstructor
public class ExportFileCleanupJob implements ScheduledJob {

    private static final int BATCH_LIMIT = 200;

    private final ExportTaskService exportTaskService;

    @Override
    public String key() {
        return "export-file-cleanup";
    }

    @Override
    public String description() {
        return "清理超过 24 小时的导出临时文件";
    }

    @Override
    public JobResult run(JobContext context) {
        int total = 0;
        int cleaned;
        do {
            cleaned = exportTaskService.cleanupExpired(BATCH_LIMIT);
            total += cleaned;
        } while (cleaned >= BATCH_LIMIT);
        return new JobResult(total, "清理 " + total + " 个过期导出文件");
    }
}
