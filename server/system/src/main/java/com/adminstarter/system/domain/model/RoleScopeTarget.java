package com.adminstarter.system.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 角色自定义数据范围目标（CUSTOM_DEPT / CUSTOM_USER）。
 */
@Data
@AllArgsConstructor
public class RoleScopeTarget {

    private Long roleId;
    private String targetType;
    private Long targetId;
}
