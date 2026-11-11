package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class DeptDO extends BaseDO {

    private Long parentId;
    private String ancestors;
    private String name;
    private Integer sort;
    private String leader;
    private String phone;
    private Boolean isEnabled;
}
