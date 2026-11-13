package com.adminstarter.system.domain.query;

import lombok.Data;

/** 客户档案列表查询条件。 */
@Data
public class CustomerQuery {

    private String keyword;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}
