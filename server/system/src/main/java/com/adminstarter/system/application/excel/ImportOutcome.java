package com.adminstarter.system.application.excel;

/**
 * 导入结果：数据行数与实际写入行数。
 */
public record ImportOutcome(long totalRows, int imported) {
}
