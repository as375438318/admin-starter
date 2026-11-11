package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Post extends BaseModel {

    private Long deptId;
    private String code;
    private String name;
    private Integer sort;
    private Boolean isEnabled;
}
