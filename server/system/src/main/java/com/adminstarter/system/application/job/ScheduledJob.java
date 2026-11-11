package com.adminstarter.system.application.job;

/**
 * 可被定时调度的任务契约。实现类必须是 Spring Bean，并且 {@link #key()} 全局唯一。
 * <p>框架只按已注册标识调度；禁止任何形式的脚本/命令/SQL 动态执行。</p>
 */
public interface ScheduledJob {

    /** 已注册的任务标识，对应 sys_job.job_key。 */
    String key();

    /** 供管理端下拉展示的说明。 */
    String description();

    /** 执行任务；返回影响条数等信息。抛异常表示本次执行失败。 */
    JobResult run(JobContext context) throws Exception;
}
