package com.adminstarter.system.domain.query;

import lombok.Data;

/** 变更审计查询条件。 */
@Data
public class ChangeAuditQuery {

    private String targetType;
    private String targetKey;
    private String operator;
    private String action;
    /** 关键词：同时匹配 target_name / target_key */
    private String keyword;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}