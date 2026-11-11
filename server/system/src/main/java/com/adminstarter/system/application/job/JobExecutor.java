package com.adminstarter.system.application.job;

import com.adminstarter.system.domain.model.JobDefinition;
import com.adminstarter.system.domain.model.JobLog;
import com.adminstarter.system.domain.repository.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务执行器：并发策略、超时、重试、日志落库、失败通知。
 * <p>并发控制是进程内计数，单机有效；多实例会各自执行，见 docs/design/scheduled-task.md §8。</p>
 */
@Slf4j
@Component
public class JobExecutor {

    public static final String CONCURRENCY_DISALLOW = "DISALLOW";
    public static final String CONCURRENCY_ALLOW = "ALLOW";

    private static final int MAX_STACK_LENGTH = 8000;

    private final JobRepository jobRepository;
    private final JobRegistry jobRegistry;
    private final JobLogAppender logAppender;
    private final JobFailureNotifier failureNotifier;

    /** 自动触发提交到这里，避免占用调度线程。 */
    private final ThreadPoolTaskExecutor runnerPool;
    /** 实际任务执行线程，用于实现超时等待。 */
    private final ThreadPoolTaskExecutor taskPool;
    /** cron 计算时区，由 app.job.zone 显式指定；不取 JVM 默认时区（容器默认 UTC 会整体偏移）。 */
    private final ZoneId zone;

    private final Map<Long, AtomicInteger> activeCount = new ConcurrentHashMap<>();

    public JobExecutor(JobRepository jobRepository,
                       JobRegistry jobRegistry,
                       JobLogAppender logAppender,
                       JobFailureNotifier failureNotifier,
                       @Qualifier("jobRunnerPool") ThreadPoolTaskExecutor runnerPool,
                       @Qualifier("jobTaskPool") ThreadPoolTaskExecutor taskPool,
                       @Value("${app.job.zone:Asia/Shanghai}") String jobZone) {
        this.jobRepository = jobRepository;
        this.jobRegistry = jobRegistry;
        this.logAppender = logAppender;
        this.failureNotifier = failureNotifier;
        this.runnerPool = runnerPool;
        this.taskPool = taskPool;
        this.zone = ZoneId.of(jobZone);
    }

    public boolean isRunning(Long jobId) {
        AtomicInteger counter = activeCount.get(jobId);
        return counter != null && counter.get() > 0;
    }

    /** 自动触发：异步提交，调度线程立即返回。 */
    public void submitAuto(Long jobId) {
        try {
            runnerPool.execute(() -> executeById(jobId, JobLog.TRIGGER_AUTO));
        } catch (RejectedExecutionException e) {
            log.error("任务 {} 自动触发被线程池拒绝: {}", jobId, e.getMessage());
        }
    }

    /** 同步执行并返回本次日志；手动执行与自动执行共用同一条路径。 */
    public JobLog executeById(Long jobId, String triggerType) {
        JobDefinition job = jobRepository.findById(jobId);
        if (job == null) {
            log.warn("任务 {} 已不存在，跳过执行", jobId);
            return null;
        }
        if (JobLog.TRIGGER_AUTO.equals(triggerType) && !job.enabled()) {
            log.warn("任务 {} 已停用，跳过自动执行", jobId);
            return null;
        }
        return execute(job, triggerType);
    }

    private JobLog execute(JobDefinition job, String triggerType) {
        AtomicInteger counter = activeCount.computeIfAbsent(job.getId(), k -> new AtomicInteger());
        boolean disallow = !CONCURRENCY_ALLOW.equals(job.getConcurrencyPolicy());
        if (disallow) {
            int previous = counter.getAndIncrement();
            if (previous > 0) {
                counter.decrementAndGet();
                return writeSkipped(job, triggerType);
            }
        } else {
            counter.incrementAndGet();
        }
        try {
            return doExecute(job, triggerType);
        } finally {
            counter.decrementAndGet();
        }
    }

    private JobLog doExecute(JobDefinition job, String triggerType) {
        LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);
        long startNanos = System.nanoTime();

        int timeoutSeconds = positive(job.getTimeoutSeconds(), 60);
        int retryTimes = nonNegative(job.getRetryTimes(), 0);
        int retryInterval = nonNegative(job.getRetryIntervalSeconds(), 10);
        int maxAttempts = 1 + retryTimes;

        String result = JobLog.RESULT_FAIL;
        int attempts = 0;
        long affectedRows = 0;
        String errorSummary = null;
        String errorStack = null;

        ScheduledJob bean = jobRegistry.require(job.getJobKey());
        if (bean == null) {
            attempts = 1;
            errorSummary = truncate("任务标识未注册：" + job.getJobKey(), 255);
        } else {
            JobContext context = new JobContext(job.getId(), job.getJobKey(), job.getName(), triggerType);
            for (int i = 0; i < maxAttempts; i++) {
                attempts++;
                Future<JobResult> future = null;
                try {
                    future = taskPool.submit(() -> bean.run(context));
                    JobResult jobResult = future.get(timeoutSeconds, TimeUnit.SECONDS);
                    result = JobLog.RESULT_SUCCESS;
                    affectedRows = jobResult == null ? 0 : jobResult.affectedRows();
                    errorSummary = null;
                    errorStack = null;
                    break;
                } catch (TimeoutException e) {
                    result = JobLog.RESULT_TIMEOUT;
                    if (future != null) {
                        future.cancel(true);
                    }
                    errorSummary = truncate("执行超时（" + timeoutSeconds + " 秒）", 255);
                    errorStack = truncate("TimeoutException: 执行超时（" + timeoutSeconds + " 秒）", MAX_STACK_LENGTH);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    result = JobLog.RESULT_FAIL;
                    errorSummary = truncate(cause.getClass().getName() + ": " + safeMessage(cause), 255);
                    errorStack = truncate(stackTrace(cause), MAX_STACK_LENGTH);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result = JobLog.RESULT_FAIL;
                    errorSummary = truncate("执行线程被中断", 255);
                    errorStack = truncate(stackTrace(e), MAX_STACK_LENGTH);
                    break;
                } catch (RejectedExecutionException e) {
                    result = JobLog.RESULT_FAIL;
                    errorSummary = truncate("任务线程池已满：" + safeMessage(e), 255);
                    errorStack = truncate(stackTrace(e), MAX_STACK_LENGTH);
                    break;
                }
                if (i < maxAttempts - 1 && retryInterval > 0) {
                    try {
                        Thread.sleep(retryInterval * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        LocalDateTime finishedAt = LocalDateTime.now(ZoneOffset.UTC);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        JobLog logRow = new JobLog();
        logRow.setJobId(job.getId());
        logRow.setJobName(job.getName());
        logRow.setJobKey(job.getJobKey());
        logRow.setTriggerType(triggerType);
        logRow.setStartedAt(startedAt);
        logRow.setFinishedAt(finishedAt);
        logRow.setElapsedMs(elapsedMs);
        logRow.setResult(result);
        logRow.setAttemptCount(attempts);
        logRow.setErrorSummary(errorSummary);
        logRow.setErrorStack(errorStack);
        logRow.setAffectedRows(affectedRows);

        appendLog(logRow);
        updateLastRun(job, startedAt, result);
        if (JobLog.TRIGGER_AUTO.equals(triggerType)) {
            updateNextRunAt(job);
        }
        // 通知器内部只对 FAIL / TIMEOUT 发送，这里统一调用。
        try {
            failureNotifier.notifyIfNeeded(job, logRow);
        } catch (Throwable e) {
            log.error("任务 {} 失败通知流程异常: {}", job.getId(), e.getMessage(), e);
        }
        return logRow;
    }

    private JobLog writeSkipped(JobDefinition job, String triggerType) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        JobLog logRow = new JobLog();
        logRow.setJobId(job.getId());
        logRow.setJobName(job.getName());
        logRow.setJobKey(job.getJobKey());
        logRow.setTriggerType(triggerType);
        logRow.setStartedAt(now);
        logRow.setFinishedAt(now);
        logRow.setElapsedMs(0L);
        logRow.setResult(JobLog.RESULT_SKIPPED);
        logRow.setAttemptCount(0);
        logRow.setErrorSummary("并发策略为禁止并发，上一次执行尚未结束");
        logRow.setAffectedRows(0L);
        appendLog(logRow);
        updateLastRun(job, now, JobLog.RESULT_SKIPPED);
        return logRow;
    }

    private void appendLog(JobLog logRow) {
        try {
            logAppender.append(logRow);
        } catch (Throwable e) {
            log.error("任务 {} 执行日志落库失败: {}", logRow.getJobId(), e.getMessage(), e);
        }
    }

    private void updateLastRun(JobDefinition job, LocalDateTime lastRunAt, String result) {
        try {
            jobRepository.updateLastRun(job.getId(), lastRunAt, result);
        } catch (Throwable e) {
            log.error("任务 {} 最近执行状态回写失败: {}", job.getId(), e.getMessage(), e);
        }
    }

    private void updateNextRunAt(JobDefinition job) {
        try {
            LocalDateTime next = nextRun(job.getCronExpression());
            jobRepository.updateNextRunAt(job.getId(), next);
        } catch (Throwable e) {
            log.error("任务 {} 下次执行时间回写失败: {}", job.getId(), e.getMessage(), e);
        }
    }

    /** 按 cron 计算下次执行时间（返回 UTC，用于回写 sys_job.next_run_at 与接口展示）。 */
    public LocalDateTime nextRun(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return null;
        }
        try {
            LocalDateTime nextLocal = org.springframework.scheduling.support.CronExpression
                    .parse(cronExpression).next(LocalDateTime.now(zone));
            return toUtc(nextLocal);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public LocalDateTime toUtc(LocalDateTime local) {
        return local == null ? null : local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static int nonNegative(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private static String safeMessage(Throwable e) {
        return e.getMessage() == null ? "" : e.getMessage();
    }

    private static String stackTrace(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
