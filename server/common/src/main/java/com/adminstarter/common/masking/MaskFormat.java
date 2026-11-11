package com.adminstarter.common.masking;

/**
 * 字段级掩码格式：掩码算法唯一定义处（见 docs/design/field-masking.md §3.2）。
 * 不匹配格式一律返回 null，由调用方按"隐藏"处理，禁止输出半掩码。
 */
public enum MaskFormat {

    /** 手机号：全为数字且长度恰为 11 → 前 3 + **** + 后 4。 */
    PHONE {
        @Override
        public String mask(String raw) {
            if (raw == null || raw.length() != 11) {
                return null;
            }
            for (int index = 0; index < raw.length(); index++) {
                if (!Character.isDigit(raw.charAt(index))) {
                    return null;
                }
            }
            return raw.substring(0, 3) + "****" + raw.substring(7);
        }
    },

    /** 邮箱：唯一 @ 且两侧非空 → 首字符 + *** + @域名（local 长度 1 时为 *@域名）。 */
    EMAIL {
        @Override
        public String mask(String raw) {
            if (raw == null) {
                return null;
            }
            int at = raw.indexOf('@');
            if (at <= 0 || at != raw.lastIndexOf('@') || at == raw.length() - 1) {
                return null;
            }
            String local = raw.substring(0, at);
            String domain = raw.substring(at + 1);
            if (local.isEmpty() || domain.isEmpty()) {
                return null;
            }
            return (local.length() >= 2 ? String.valueOf(local.charAt(0)) : "") + "***" + "@" + domain;
        }
    };

    public abstract String mask(String raw);
}
