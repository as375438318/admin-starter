package com.adminstarter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 系统参数中敏感项的展示掩码。 */
class MaskUtilsTest {

    @Test
    @DisplayName("非空值 → 固定 6 个星号（不泄露长度）")
    void nonEmptyBecomesFixedMask() {
        assertEquals("******", MaskUtils.mask("s3cr3t"));
        assertEquals("******", MaskUtils.mask("a"));
        assertEquals(MaskUtils.MASK, MaskUtils.mask("很长的一段口令很长的一段口令"));
    }

    @Test
    @DisplayName("null 与空串原样返回，避免把「未设置」显示成「已设置」")
    void nullAndEmptyPassThrough() {
        assertNull(MaskUtils.mask(null));
        assertEquals("", MaskUtils.mask(""));
    }
}
