package com.adminstarter.common.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 掩码算法（{@code docs/design/field-masking.md} 的唯一定义处）。
 * <p>关键约定：**格式不匹配一律返回 null**，由调用方按"隐藏"处理——禁止输出半掩码，
 * 否则 138**** 这种残缺值会被当成真实数据展示甚至回写。</p>
 */
class MaskFormatTest {

    @Test
    @DisplayName("手机号：11 位纯数字 → 前 3 + **** + 后 4")
    void phoneMasksMiddle() {
        assertEquals("138****8000", MaskFormat.PHONE.mask("13800138000"));
        assertEquals("100****0000", MaskFormat.PHONE.mask("10000000000"));
    }

    @Test
    @DisplayName("手机号：长度不等于 11 或含非数字 → null（不做半掩码）")
    void phoneRejectsNonElevenDigitOrNonNumeric() {
        assertNull(MaskFormat.PHONE.mask(null));
        assertNull(MaskFormat.PHONE.mask(""));
        assertNull(MaskFormat.PHONE.mask("1380013800"));      // 10 位
        assertNull(MaskFormat.PHONE.mask("138001380000"));    // 12 位
        assertNull(MaskFormat.PHONE.mask("138-0013-800"));    // 含符号
        assertNull(MaskFormat.PHONE.mask("1380013800a"));     // 含字母
    }

    @Test
    @DisplayName("手机号：实现用 Character.isDigit，全角/其它 Unicode 数字也会被接受（现状，非缺陷但需知晓）")
    void phoneAcceptsUnicodeDigits() {
        // 观察项：docs/design/field-masking.md 写的是「全为数字」，实现取 Character.isDigit，
        // 因此 '１'(U+FF11)、'٣'(Arabic-Indic) 都算数字。正常号码不受影响，展示仍是掩码形态。
        // 若产品要求「只收 ASCII 数字」，需把判断改为 '0' <= c && c <= '9'——那是行为变更，另行决策。
        assertEquals("１３８****８０００", MaskFormat.PHONE.mask("１３８００１３８０００"));
    }

    @Test
    @DisplayName("邮箱：首字符 + *** + @域名")
    void emailMasksLocalPart() {
        assertEquals("a***@example.com", MaskFormat.EMAIL.mask("admin@example.com"));
        assertEquals("z***@a.cn", MaskFormat.EMAIL.mask("zhang@a.cn"));
    }

    @Test
    @DisplayName("邮箱：local 只有 1 个字符时不泄露任何字符")
    void emailSingleCharLocalPartHidesChar() {
        assertEquals("***@example.com", MaskFormat.EMAIL.mask("a@example.com"));
    }

    @Test
    @DisplayName("邮箱：无法确定唯一 @ 或两侧为空 → null")
    void emailRejectsMalformed() {
        assertNull(MaskFormat.EMAIL.mask(null));
        assertNull(MaskFormat.EMAIL.mask(""));
        assertNull(MaskFormat.EMAIL.mask("no-at-sign"));
        assertNull(MaskFormat.EMAIL.mask("@example.com"));       // @ 在首位
        assertNull(MaskFormat.EMAIL.mask("admin@"));              // 域名为空
        assertNull(MaskFormat.EMAIL.mask("a@b@example.com"));     // 两个 @
    }
}
