package com.adminstarter.system.application.export;

import java.util.Arrays;
import java.util.List;

/**
 * 导出列白名单参数的解析（`?fields=title,status`）。
 *
 * <p>只做"解析成去重列表"，<b>不做合法性校验</b>：key 是否属于 schema 由
 * {@code ExcelExportWriter#resolveColumns} 在真正写文件时判定（400 + 未知列名），
 * 避免同一份校验逻辑散落在控制器、AppService 与 Writer 三处。</p>
 */
public final class ExportFieldKeys {

    private ExportFieldKeys() {
    }

    /** null / 空串 → 空列表（表示"不裁剪，导出全部可导出列"） */
    public static List<String> parse(String fields) {
        if (fields == null || fields.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();
    }
}