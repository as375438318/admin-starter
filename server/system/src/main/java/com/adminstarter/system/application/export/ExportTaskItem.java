package com.adminstarter.system.application.export;

import lombok.Data;

import java.time.Instant;

/**
 * 导出任务列表项：所有模块共用同一结构，避免各模块各写一份 DTO。
 */
@Data
public class ExportTaskItem {

    private Long id;
    private String fileName;
    private long totalRows;
    private int progress;
    private String status;
    private boolean cancelRequested;
    private String errorMessage;
    private Long fileSize;
    private Instant createdAt;
    private Instant expiresAt;
}
