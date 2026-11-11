package com.adminstarter.system.application.export;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动补偿：上次进程退出时未完成的导出任务标记失败并清理半成品文件。
 */
@Component
@RequiredArgsConstructor
public class ExportTaskStartupCompensation {

    private final ExportTaskService exportTaskService;

    @EventListener(ApplicationReadyEvent.class)
    public void compensate() {
        exportTaskService.compensateInterrupted();
    }
}
