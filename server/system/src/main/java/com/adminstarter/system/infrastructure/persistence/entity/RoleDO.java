package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class RoleDO extends BaseDO {

    private String name;
    private String code;
    private Integer sort;
    private String dataScope;
    private String ownFilter;
    private String remark;
    private Boolean isEnabled;
}
