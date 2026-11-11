package com.adminstarter.system.application.excel;

/**
 * 字段类别：决定「显示值 ↔ 存储值」由框架统一翻译，业务模块不各写一份。
 * <ul>
 *   <li>{@link #TEXT}：原样文本，导入走 {@link ExcelField#getImporter()}，导出走 {@link ExcelField#getExporter()}。</li>
 *   <li>{@link #ENUM}：导入接受存储值或中文含义，导出统一中文含义；可选值来自 {@link ExcelField#getEnums()}。</li>
 *   <li>{@link #REF_DEPT}：存储部门 ID，显示部门完整路径（如「总部/研发部/前端组」）。</li>
 *   <li>{@link #REF_USER}：存储用户 ID，显示「姓名（部门完整路径）」，导入同时接受账号。</li>
 * </ul>
 */
public enum FieldKind {
    TEXT,
    ENUM,
    REF_DEPT,
    REF_USER
}
