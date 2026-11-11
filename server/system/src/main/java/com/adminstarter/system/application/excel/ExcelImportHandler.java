package com.adminstarter.system.application.excel;

import java.util.List;
import java.util.Map;

/**
 * 业务模块必须实现的导入钩子。元数据校验由框架先做，业务只做引用数据与实体转换、落库。
 */
public interface ExcelImportHandler<T, C> {

    ExcelSchema<?> schema();

    /** 一次性批量查引用数据（部门、用户等），禁止逐行查库。 */
    C prepare(List<RawRow> rows);

    /** 行级业务校验；fieldLabel 使用表头名称，行号由框架拼接。 */
    void validate(RawRow row, Map<String, Object> values, C context, ImportErrorCollector errors);

    T toEntity(Map<String, Object> values);

    /** 在调用方事务内批量写入。 */
    void persist(List<T> entities);
}
