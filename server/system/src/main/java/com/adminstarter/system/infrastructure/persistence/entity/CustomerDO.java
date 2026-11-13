package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_customer")
public class CustomerDO extends BaseDO {
    private String name;
    private String level;
    private String contact;
    private String phone;
    private Boolean isEnabled;
    private String remark;
}
