package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 异步导出任务。临时文件路径只在服务端使用，不进入 sys_file。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExportTask extends BaseModel {

    private Long userId;
    private String username;
    private String module;
    private String fileName;
    /** 导出过滤条件快照（JSON），执行时用于重建同一查询。 */
    private String queryParams;
    private long totalRows;
    private int progress;
    private ExportTaskStatus status;
    private boolean cancelRequested;
    private String errorMessage;
    private String filePath;
    private Long fileSize;
    private LocalDateTime expiresAt;
}
