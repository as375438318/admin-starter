package com.adminstarter.system.domain.query;

import lombok.Data;

@Data
public class JobQuery {

    private String keyword;
    private String groupName;
    private String status;
    private int page;
    private int pageSize;
    private String sort;
    private String order;
}
