package com.adminstarter.system.application.job;

/**
 * 任务执行结果：影响条数与简短说明。
 */
public record JobResult(long affectedRows, String message) {

    public static JobResult ok(long affectedRows) {
        return new JobResult(affectedRows, "ok");
    }
}
