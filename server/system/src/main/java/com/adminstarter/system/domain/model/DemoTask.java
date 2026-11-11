package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 示例业务任务：数据权限参考实现的领域模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DemoTask extends BaseModel {

    private String title;
    private String status;
    private Long deptId;
    private Long ownerId;
    private Long createBy;
}
