package com.adminstarter.system.application.excel.demo;

import lombok.Data;

/**
 * 示例任务导出行：实体字段 + 关联名称 + 脱敏原始值。只服务导出，不参与导入。
 */
@Data
public class DemoTaskExportRow {

    private Long id;
    private String title;
    private String status;
    /** 部门 ID；显示为完整路径由框架统一翻译。 */
    private Long deptId;
    /** 负责人 ID；显示为「姓名（部门）」由框架统一翻译。 */
    private Long ownerId;
    /** 原始手机号；是否打码/整列隐藏由 ExcelExportWriter 的 MaskResolver 判定。 */
    private String ownerPhone;
    private String createdAt;
}
