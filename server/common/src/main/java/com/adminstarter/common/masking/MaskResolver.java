package com.adminstarter.common.masking;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;

/**
 * 三态判定（唯一实现）：有 view 明文；无 view 有 mask 掩码；都没有（含未登录）隐藏；空值原样。
 * 判定只依赖当前用户权限集合，不按角色编码特判；超管同样需要显式授权。
 */
public final class MaskResolver {

    private MaskResolver() {
    }

    public static Resolution resolve(String fieldKey, MaskFormat format, String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Resolution(null, raw);
        }
        if (MaskSelfReadContext.isEnabled()) {
            return new Resolution(null, raw);
        }
        CurrentUser current = DataScopeContext.get();
        if (current == null) {
            return new Resolution(MaskState.HIDDEN, null);
        }
        if (current.hasPermission(permission(fieldKey, "view"))) {
            return new Resolution(null, raw);
        }
        if (current.hasPermission(permission(fieldKey, "mask"))) {
            String masked = format.mask(raw);
            return masked == null ? new Resolution(MaskState.HIDDEN, null) : new Resolution(MaskState.MASKED, masked);
        }
        return new Resolution(MaskState.HIDDEN, null);
    }

    public static boolean canView(String fieldKey) {
        CurrentUser current = DataScopeContext.get();
        return current != null && current.hasPermission(permission(fieldKey, "view"));
    }

    public static boolean canMask(String fieldKey) {
        CurrentUser current = DataScopeContext.get();
        return current != null && current.hasPermission(permission(fieldKey, "mask"));
    }

    public static String permission(String fieldKey, String action) {
        return "system:" + fieldKey + ":" + action;
    }

    /** state 为 null 表示明文。 */
    public record Resolution(MaskState state, String value) {
    }
}
