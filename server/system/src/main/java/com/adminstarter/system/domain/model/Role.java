package com.adminstarter.system.domain.model;

import com.adminstarter.system.domain.enums.DataScope;
import com.adminstarter.system.domain.enums.OwnFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseModel {

    private String name;
    private String code;
    private Integer sort;
    private DataScope dataScope;
    private OwnFilter ownFilter;
    private String remark;
    private Boolean isEnabled;
}
