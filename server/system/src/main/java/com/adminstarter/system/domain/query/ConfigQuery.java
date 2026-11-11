package com.adminstarter.system.domain.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConfigQuery {

    private String keyword;
    private Boolean isSensitive;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}
