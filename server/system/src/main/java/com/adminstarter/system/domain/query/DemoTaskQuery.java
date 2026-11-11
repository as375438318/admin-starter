package com.adminstarter.system.domain.query;

import lombok.Data;

@Data
public class DemoTaskQuery {

    private String keyword;
    private String status;
    private Long deptId;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}
