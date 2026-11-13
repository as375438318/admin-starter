package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户档案（由 tools/gen-module 生成，之后按需手改；生成器不做双向同步）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Customer extends BaseModel {
    private String name;
    private String level;
    private String contact;
    private String phone;
    private Boolean isEnabled;
    private String remark;
}
