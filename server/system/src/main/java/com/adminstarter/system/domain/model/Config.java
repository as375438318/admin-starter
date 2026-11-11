package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Config extends BaseModel {

    private String configKey;
    private String configValue;
    private String name;
    private String remark;
    private Boolean isSensitive;
    private Boolean isEnabled;
}
