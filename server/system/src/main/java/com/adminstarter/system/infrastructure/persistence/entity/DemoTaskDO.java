package com.adminstarter.system.infrastructure.persistence.entity;

import com.adminstarter.system.infrastructure.persistence.DataPermissionControlled;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_demo_task")
@DataPermissionControlled(ownerColumn = "owner_id", selfColumn = "create_by")
public class DemoTaskDO extends BaseDO {

    private String title;
    private String status;
    private Long deptId;
    private Long ownerId;
    private Long createBy;
}
