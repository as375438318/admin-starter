package com.adminstarter.system.domain.enums;

/**
 * 组织范围六档。多角色取并集，见 docs 任务说明。
 */
public enum DataScope {

    ALL,
    DEPT,
    DEPT_AND_CHILD,
    SELF,
    CUSTOM_DEPT,
    CUSTOM_USER
}
