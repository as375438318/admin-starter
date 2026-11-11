package com.adminstarter.system.application.job;

import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.domain.repository.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 调度器：每个启用任务持有独立 {@link ScheduledFuture}，改 cron 时取消后按新表达式重建。
 *
 * <p>时区由 {@code app.job.zone} 显式指定（默认 Asia/Shanghai），**不取 JVM 默认时区**：
 * 容器镜像默认 UTC，用系统时区会让 cron 整体偏移。库内 `next_run_at` 仍按 UTC 存储。</p>
 *
 * <p>单机调度：调度状态只在进程内；多实例会重复执行，见 `docs/design/scheduled-task.md` §8。</p>
 */
@Slf4j
@Component
public class JobScheduler {

    private final JobRepository jobRepository;
    private final JobExecutor jobExecutor;
    private final ThreadPoolTaskScheduler scheduler;
    private final ZoneId zone;
    private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public JobScheduler(JobRepository jobRepository,
                        JobExecutor jobExecutor,
                        @Qualifier("jobSchedulerPool") ThreadPoolTaskScheduler scheduler,
                        @Value("${app.job.zone:Asia/Shanghai}") String jobZone) {
        this.jobRepository = jobRepository;
        this.jobExecutor = jobExecutor;
        this.scheduler = scheduler;
        this.zone = ZoneId.of(jobZone);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("定时任务生效时区：{}（app.job.zone，库内时间为 UTC）", zone);
        for (JobDefinition job : jobRepository.findEnabled()) {
            try {
                register(job);
            } catch (RuntimeException e) {
                log.error("任务 {} 启动注册失败: {}", job.getId(), e.getMessage(), e);
            }
        }
        log.info("定时任务启动注册完成，已启用 {} 个", futures.size());
    }

    /** 任务新增/修改/启停后调用：取消旧调度并按当前状态重建。 */
    public void refresh(JobDefinition job) {
        cancelFuture(job.getId());
        if (job.enabled()) {
            register(job);
        } else {
            safeUpdateNextRunAt(job.getId(), null);
        }
    }

    /** 删除任务时调用：取消调度并清空下次执行时间。 */
    public void cancel(Long jobId) {
        cancelFuture(jobId);
        safeUpdateNextRunAt(jobId, null);
    }

    public boolean isRunning(Long jobId) {
        return jobExecutor.isRunning(jobId);
    }

    public LocalDateTime nextRunAt(JobDefinition job) {
        if (job == null || !job.enabled()) {
            return null;
        }
        try {
            return toUtc(CronExpression.parse(job.getCronExpression()).next(LocalDateTime.now(zone)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void register(JobDefinition job) {
        CronExpression.parse(job.getCronExpression());
        Long jobId = job.getId();
        ScheduledFuture<?> future = scheduler.schedule(
                () -> jobExecutor.submitAuto(jobId),
                new CronTrigger(job.getCronExpression()));
        futures.put(jobId, future);
        safeUpdateNextRunAt(jobId, nextRunAt(job));
        log.info("任务 {}（{}）已注册，cron={}", jobId, job.getJobKey(), job.getCronExpression());
    }

    private void cancelFuture(Long jobId) {
        ScheduledFuture<?> future = futures.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void safeUpdateNextRunAt(Long jobId, LocalDateTime nextRunAt) {
        try {
            jobRepository.updateNextRunAt(jobId, nextRunAt);
        } catch (RuntimeException e) {
            log.error("任务 {} 下次执行时间回写失败: {}", jobId, e.getMessage(), e);
        }
    }

    private LocalDateTime toUtc(LocalDateTime local) {
        return local == null ? null : local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
