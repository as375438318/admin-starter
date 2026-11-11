package com.adminstarter.system.domain.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperLogQuery {

    private String keyword;
    private Integer resultCode;
    private LocalDateTime from;
    private LocalDateTime to;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
}
