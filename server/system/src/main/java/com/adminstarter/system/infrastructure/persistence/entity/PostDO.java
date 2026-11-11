package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class PostDO extends BaseDO {

    private Long deptId;
    private String code;
    private String name;
    private Integer sort;
    private Boolean isEnabled;
}
