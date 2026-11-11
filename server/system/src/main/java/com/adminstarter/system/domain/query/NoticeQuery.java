package com.adminstarter.system.domain.query;

import lombok.Data;

@Data
public class NoticeQuery {

    private String keyword;
    private String level;
    private String type;
    /** PUBLISHED / REVOKED，空表示全部 */
    private String status;
    private Long deptId;
    private int page;
    private int pageSize;
    private String sort;
    private String order;
}
