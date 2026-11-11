package com.adminstarter.system.domain.query;

import lombok.Data;

@Data
public class JobLogQuery {

    private Long jobId;
    private String keyword;
    private String result;
    private String triggerType;
    private int page;
    private int pageSize;
}
