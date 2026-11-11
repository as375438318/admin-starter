package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class OperLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private Long deptId;
    private String module;
    private String action;
    private String method;
    private String requestUri;
    private String requestMethod;
    private String ip;
    private String params;
    private Integer resultCode;
    private String resultMessage;
    private Long elapsedMs;
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
