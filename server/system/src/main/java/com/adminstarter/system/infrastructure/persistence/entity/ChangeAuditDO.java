package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_change_audit")
public class ChangeAuditDO extends BaseDO {

    private String targetType;
    private String targetKey;
    private String targetName;
    private String action;
    private String beforeValue;
    private String afterValue;
    private Boolean isSensitive;
    private Long operatorId;
    private String operatorName;
    private String ip;
    private String traceId;
}