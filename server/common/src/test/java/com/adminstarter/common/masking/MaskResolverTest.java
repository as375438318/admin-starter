package com.adminstarter.common.masking;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段三态判定（唯一实现）：有 view → 明文；有 mask → 掩码；都没有 → 隐藏；空值原样。
 * <p>判定只依赖权限集合，不按角色编码特判；未登录同样走"隐藏"（fail-closed）。</p>
 */
class MaskResolverTest {

    private static CurrentUser userWith(String... permissions) {
        return CurrentUser.builder()
                .userId(1L)
                .username("tester")
                .permissions(Set.of(permissions))
                .build();
    }

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("有 view 权限 → 明文（state 为 null）")
    void viewPermissionReturnsPlainText() {
        DataScopeContext.set(userWith("system:user.phone:view"));

        MaskResolver.Resolution resolution = MaskResolver.resolve("user.phone", MaskFormat.PHONE, "13800138000");

        assertNull(resolution.state(), "明文不登记状态");
        assertEquals("13800138000", resolution.value());
    }

    @Test
    @DisplayName("只有 mask 权限 → MASKED + 掩码值")
    void maskPermissionReturnsMasked() {
        DataScopeContext.set(userWith("system:user.phone:mask"));

        MaskResolver.Resolution resolution = MaskResolver.resolve("user.phone", MaskFormat.PHONE, "13800138000");

        assertEquals(MaskState.MASKED, resolution.state());
        assertEquals("138****8000", resolution.value());
    }

    @Test
    @DisplayName("有 mask 权限但格式不匹配 → 退化为 HIDDEN（不输出半掩码）")
    void maskPermissionWithMalformedValueFallsBackToHidden() {
        DataScopeContext.set(userWith("system:user.phone:mask"));

        MaskResolver.Resolution resolution = MaskResolver.resolve("user.phone", MaskFormat.PHONE, "not-a-phone");

        assertEquals(MaskState.HIDDEN, resolution.state());
        assertNull(resolution.value());
    }

    @Test
    @DisplayName("没有任何相关权限 → HIDDEN，且不返回值")
    void noPermissionHides() {
        DataScopeContext.set(userWith("system:user:list"));

        MaskResolver.Resolution resolution = MaskResolver.resolve("user.email", MaskFormat.EMAIL, "admin@example.com");

        assertEquals(MaskState.HIDDEN, resolution.state());
        assertNull(resolution.value());
    }

    @Test
    @DisplayName("未登录（无上下文）→ HIDDEN，fail-closed")
    void anonymousHides() {
        MaskResolver.Resolution resolution = MaskResolver.resolve("user.email", MaskFormat.EMAIL, "admin@example.com");

        assertEquals(MaskState.HIDDEN, resolution.state());
        assertNull(resolution.value());
    }

    @Test
    @DisplayName("空值原样返回：null 与空串都不登记状态，也不触发权限判定")
    void emptyValuePassesThroughUntouched() {
        MaskResolver.Resolution forNull = MaskResolver.resolve("user.phone", MaskFormat.PHONE, null);
        assertNull(forNull.state());
        assertNull(forNull.value());

        MaskResolver.Resolution forEmpty = MaskResolver.resolve("user.phone", MaskFormat.PHONE, "");
        assertNull(forEmpty.state());
        assertEquals("", forEmpty.value());
    }

    @Test
    @DisplayName("权限码由字段标识拼装：system:<fieldKey>:<action>")
    void permissionKeyIsComposed() {
        assertEquals("system:user.phone:view", MaskResolver.permission("user.phone", "view"));
        assertEquals("system:user.email:mask", MaskResolver.permission("user.email", "mask"));
    }

    @Test
    @DisplayName("canView / canMask 只认显式授权，未登录一律 false")
    void canViewAndCanMaskRequireContext() {
        assertFalse(MaskResolver.canView("user.phone"));
        assertFalse(MaskResolver.canMask("user.phone"));

        DataScopeContext.set(userWith("system:user.phone:view"));
        assertTrue(MaskResolver.canView("user.phone"));
        assertFalse(MaskResolver.canMask("user.phone"), "有 view 不等于有 mask");
    }
}
