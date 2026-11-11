package com.adminstarter.system.application.excel;

import java.util.Map;

/**
 * 解析后的一行：保留 Excel 真实行号与「表头 → 单元格文本」，不保留 POI 的 Row/Cell 对象树。
 */
public record RawRow(int rowNumber, Map<String, String> cells) {
}
