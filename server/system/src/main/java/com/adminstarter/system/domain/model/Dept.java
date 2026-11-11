package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Dept extends BaseModel {

    private Long parentId;
    private String ancestors;
    private String name;
    private Integer sort;
    private String leader;
    private String phone;
    private Boolean isEnabled;
}
