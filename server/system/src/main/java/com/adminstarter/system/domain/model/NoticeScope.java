package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接收范围规则：scopeType = ALL / ROLE / DEPT / USER，targetId 对应角色/部门/用户 id。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeScope extends BaseModel {

    private Long noticeId;
    private String scopeType;
    private Long targetId;
}
