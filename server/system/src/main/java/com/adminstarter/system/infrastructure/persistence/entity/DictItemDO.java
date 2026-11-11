package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class DictItemDO extends BaseDO {

    private Long dictTypeId;
    private String label;
    private String value;
    private Integer sort;
    private Boolean isEnabled;
    private String remark;

    /** 按类型批量查询时带出的类型编码，非表字段。 */
    @TableField(exist = false)
    private String typeCode;
}
