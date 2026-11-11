package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DictType extends BaseModel {

    private String name;
    private String type;
    private String remark;
    private Boolean isEnabled;
}
