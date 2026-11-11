package com.adminstarter.system.infrastructure.persistence;

import java.util.Map;

/**
 * 排序字段白名单：客户端传入的字段必须映射到固定列，禁止直接进 ORDER BY（BE-DB-006）。
 */
public final class SortSupport {

    private SortSupport() {
    }

    public static String resolveColumn(Map<String, String> whitelist, String sort, String defaultColumn) {
        if (sort == null || sort.isBlank()) {
            return defaultColumn;
        }
        String column = whitelist.get(sort);
        return column == null ? defaultColumn : column;
    }

    public static boolean isAsc(String order) {
        return order == null || order.isBlank() || "asc".equalsIgnoreCase(order);
    }
}
