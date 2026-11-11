package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_export_task")
public class ExportTaskDO extends BaseDO {

    private Long userId;
    private String username;
    private String module;
    private String fileName;
    private String queryParams;
    private Long totalRows;
    private Integer progress;
    private String status;
    private Boolean cancelRequested;
    private String errorMessage;
    private String filePath;
    private Long fileSize;
    private LocalDateTime expiresAt;
}
