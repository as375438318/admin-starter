package com.adminstarter.system.application.export;

/**
 * 导出任务被用户取消：用于中断写文件并按 CANCELED 收尾。
 */
public class ExportCanceledException extends RuntimeException {

    public ExportCanceledException() {
        super("导出任务已取消");
    }
}
