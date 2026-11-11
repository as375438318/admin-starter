package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictTypeDO extends BaseDO {

    private String name;
    private String type;
    private String remark;
    private Boolean isEnabled;
}
