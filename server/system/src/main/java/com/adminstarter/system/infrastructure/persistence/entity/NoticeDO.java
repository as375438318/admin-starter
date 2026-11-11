package com.adminstarter.system.infrastructure.persistence.entity;

import com.adminstarter.system.infrastructure.persistence.DataPermissionControlled;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
@DataPermissionControlled(ownerColumn = "create_by", selfColumn = "create_by")
public class NoticeDO extends BaseDO {

    private String title;
    private String content;
    private String level;
    private String type;
    @TableField("is_revocable")
    private Boolean revocable;
    private java.time.LocalDateTime revokedAt;
    private Long revokedBy;
    private Long deptId;
    private Long createBy;
}
