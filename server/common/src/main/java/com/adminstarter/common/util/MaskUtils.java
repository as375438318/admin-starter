package com.adminstarter.common.util;

/**
 * 敏感值掩码：系统参数中的敏感项在接口中只展示掩码。
 */
public final class MaskUtils {

    public static final String MASK = "******";

    private MaskUtils() {
    }

    public static String mask(String value) {
        return value == null || value.isEmpty() ? value : MASK;
    }
}
