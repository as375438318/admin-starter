package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行日志持久化对象：审计数据，无 updated_at / deleted。
 */
@Data
@TableName("sys_job_log")
public class JobLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;
    private String jobName;
    private String jobKey;
    private String triggerType;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private String result;
    private Integer attemptCount;
    private String errorSummary;
    private String errorStack;
    private Long affectedRows;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
