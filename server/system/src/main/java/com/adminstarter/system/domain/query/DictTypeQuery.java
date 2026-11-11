package com.adminstarter.system.domain.query;

import lombok.Data;

@Data
public class DictTypeQuery {

    private String keyword;
    private Boolean isEnabled;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}
