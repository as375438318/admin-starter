package com.adminstarter.system.application.job;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务相关线程池与调度器的统一装配。
 *
 * <p>改前这些池在 {@link JobExecutor} / {@link JobScheduler} 的构造器里 {@code new} 出来并手工
 * {@code initialize()}，绕过 Spring 生命周期：停机顺序不可控（只有调度器挂了 {@code @PreDestroy}，
 * 两个执行池无人关闭），池参数也写死在代码里。改为 {@code @Bean} 后由容器负责 initialize / shutdown，
 * 参数走 {@code app.job.*} 配置。</p>
 */
@Configuration
public class JobSchedulingConfig {

    /** 调度线程池：只负责按 cron 触发，不执行任务本体。 */
    @Bean("jobSchedulerPool")
    public ThreadPoolTaskScheduler jobSchedulerPool(
            @Value("${app.job.scheduler-pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("job-sched-");
        // 停机时不等待正在触发的调度：任务本体在 jobTaskPool 里，由它在 allow-termination 内收尾
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    /** 自动触发的提交池：调度线程把活丢这里，立刻返回。 */
    @Bean("jobRunnerPool")
    public ThreadPoolTaskExecutor jobRunnerPool(
            @Value("${app.job.runner-core-pool-size:2}") int corePoolSize,
            @Value("${app.job.runner-max-pool-size:8}") int maxPoolSize,
            @Value("${app.job.runner-queue-capacity:100}") int queueCapacity) {
        return buildExecutor("job-runner-", corePoolSize, maxPoolSize, queueCapacity);
    }

    /** 任务实际执行池：超时等待（{@code Future.get(timeout)}）作用在这个池的线程上。 */
    @Bean("jobTaskPool")
    public ThreadPoolTaskExecutor jobTaskPool(
            @Value("${app.job.task-core-pool-size:2}") int corePoolSize,
            @Value("${app.job.task-max-pool-size:8}") int maxPoolSize,
            @Value("${app.job.task-queue-capacity:100}") int queueCapacity) {
        return buildExecutor("job-task-", corePoolSize, maxPoolSize, queueCapacity);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int corePoolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
