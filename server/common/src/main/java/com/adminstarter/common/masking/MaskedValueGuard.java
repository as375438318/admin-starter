package com.adminstarter.common.masking;

import com.adminstarter.common.error.FieldValidationException;

/**
 * 编辑回传保护（见 docs/design/field-masking.md §6）：掩码值不得当真实值写回。
 * 所有业务写入共用本类，禁止在各模块重复实现。
 */
public final class MaskedValueGuard {

    private static final String MASK_CHAR = "*";

    private MaskedValueGuard() {
    }

    /**
     * 计算应落库的值。
     *
     * @param property  请求字段名（用于 422 的 field）
     * @param fieldKey  字段标识（如 user.phone）
     * @param format    掩码格式
     * @param currentRaw 库中原值
     * @param submitted 提交值
     * @param specified 请求体是否显式携带该字段（未携带 = 不改）
     * @param selfOwned 是否本人数据（个人中心读自己后的写路径视为可见）
     */
    public static String apply(String property, String fieldKey, MaskFormat format, String currentRaw,
                               String submitted, boolean specified, boolean selfOwned) {
        if (!specified) {
            return currentRaw;
        }
        if (submitted != null && submitted.contains(MASK_CHAR)) {
            if (submitted.equals(format.mask(currentRaw))) {
                return currentRaw;
            }
            throw new FieldValidationException(property, "INVALID_FORMAT", "不允许提交掩码值");
        }
        if (submitted == null || submitted.isEmpty()) {
            MaskResolver.Resolution resolution = MaskResolver.resolve(fieldKey, format, currentRaw);
            if (selfOwned || resolution.state() == MaskState.MASKED || currentRaw == null || currentRaw.isEmpty()) {
                return submitted;
            }
            throw new FieldValidationException(property, "VALIDATION_FAILED", "无权查看该字段，不能清空");
        }
        return submitted;
    }

    /** 创建请求禁止出现掩码字符，防止掩码串被当新值入库。 */
    public static void rejectMaskedValue(String property, String submitted) {
        if (submitted != null && submitted.contains(MASK_CHAR)) {
            throw new FieldValidationException(property, "INVALID_FORMAT", "不允许提交掩码值");
        }
    }
}
