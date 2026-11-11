package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DictItem extends BaseModel {

    private Long dictTypeId;
    private String label;
    private String value;
    private Integer sort;
    private Boolean isEnabled;
    private String remark;
    /** 按类型批量查询时带出的类型编码，非持久化字段。 */
    private String typeCode;
}
