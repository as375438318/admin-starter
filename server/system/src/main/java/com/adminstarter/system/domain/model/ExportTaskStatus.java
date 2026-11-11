package com.adminstarter.system.domain.model;

/**
 * 导出任务状态；流转见 docs/design/state-machine.md。
 */
public enum ExportTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELED,
    EXPIRED
}
