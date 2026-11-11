package com.adminstarter.system.application.excel;

import java.util.List;

/**
 * 一份 Excel 字段元数据。模板、导入校验、导出表头都从它派生。
 */
public interface ExcelSchema<T> {

    /** 模块标识，用于日志与错误提示。 */
    String key();

    /** 模块名称，用于模板标题。 */
    String title();

    /** 模板文件名，例如「示例任务导入模板.xlsx」。 */
    String templateFileName();

    /** 数据页名称，模板与导入必须一致。 */
    String dataSheetName();

    List<ExcelField<T>> fields();

    /** 模板「填写须知」页的附加区块；默认无。 */
    default List<TemplateSection> templateSections() {
        return List.of();
    }

    default List<ExcelField<T>> importFields() {
        return fields().stream().filter(field -> field.getImportHeader() != null).toList();
    }

    default List<ExcelField<T>> exportFields() {
        return fields().stream().filter(field -> field.getExportHeader() != null).toList();
    }
}
