package com.adminstarter.system.application.excel.post;

import lombok.Data;

/**
 * 岗位导出行：实体字段 + 部门名称。只服务导出，不参与导入。
 */
@Data
public class PostExportRow {

    private Long id;
    /** 部门 ID；显示为完整路径由框架统一翻译。 */
    private Long deptId;
    private String code;
    private String name;
    private Integer sort;
    private Boolean isEnabled;
    private String createdAt;
}
