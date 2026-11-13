package com.adminstarter.system.domain.query;

import lombok.Data;

@Data
public class PostQuery {

    private String keyword;
    private Long deptId;
    private Boolean isEnabled;
    private int page = 1;
    private int pageSize = 20;
    private String sort;
    private String order;
    /**
     * 导出列白名单（逗号分隔；仅导出用，不参与筛选）。随异步导出任务存进
     * `sys_export_task.query_params` 快照，worker 据此裁剪列（CR-20261113-038）。
     */
    private String fields;
}
