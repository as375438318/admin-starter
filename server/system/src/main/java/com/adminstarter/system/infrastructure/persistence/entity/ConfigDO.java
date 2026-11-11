package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class ConfigDO extends BaseDO {

    private String configKey;
    private String configValue;
    private String name;
    private String remark;
    private Boolean isSensitive;
    private Boolean isEnabled;
}
