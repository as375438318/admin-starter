package com.adminstarter.common.masking;

import com.adminstarter.common.error.FieldValidationException;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编辑回传保护（{@code docs/design/field-masking.md} 的写路径口径）。
 * <p>核心风险：前端把掩码串 138****8000 原样回传，后端当新值入库 → 真实手机号被掩码覆盖，
 * 数据不可逆丢失。因此这里必须区分"原样回传掩码"（保留原值）与"提交了别的掩码"（422）。</p>
 */
class MaskedValueGuardTest {

    private static final String KEY = "user.phone";
    private static final MaskFormat FORMAT = MaskFormat.PHONE;

    private static void asUserWith(String... permissions) {
        DataScopeContext.set(CurrentUser.builder()
                .userId(7L)
                .username("tester")
                .permissions(Set.of(permissions))
                .build());
    }

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("请求体未携带该字段 → 一律保留库中原值")
    void unspecifiedKeepsCurrentValue() {
        String applied = MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", null, false, false);
        assertEquals("13800138000", applied);
    }

    @Test
    @DisplayName("原样回传掩码（与当前原值的掩码相同）→ 保留原值，不当作新值")
    void echoingExactMaskKeepsCurrentValue() {
        String applied = MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", "138****8000", true, false);
        assertEquals("13800138000", applied);
    }

    @Test
    @DisplayName("提交了别的掩码串 → 422 INVALID_FORMAT（不能猜用户想要什么）")
    void submittingDifferentMaskIsRejected() {
        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", "139****9999", true, false));

        assertEquals("phone", error.getField());
        assertEquals("INVALID_FORMAT", error.getCode());
        assertEquals("不允许提交掩码值", error.getMessage());
    }

    @Test
    @DisplayName("提交了真实值 → 正常落库")
    void realValueIsApplied() {
        String applied = MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", "13900139000", true, false);
        assertEquals("13900139000", applied);
    }

    @Test
    @DisplayName("清空：本人数据（selfOwned）允许清空")
    void selfOwnedCanClear() {
        asUserWith(); // 无任何权限，靠 selfOwned 放行
        String applied = MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", "", true, true);
        assertEquals("", applied);
    }

    @Test
    @DisplayName("清空：有 mask 权限（能看见掩码）允许清空")
    void maskedViewerCanClear() {
        asUserWith("system:user.phone:mask");
        String applied = MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", "", true, false);
        assertEquals("", applied);
    }

    @Test
    @DisplayName("清空：只有 HIDDEN（连掩码都看不到）→ 422 VALIDATION_FAILED，防止盲改")
    void hiddenValueCannotBeCleared() {
        asUserWith("system:user:list");
        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> MaskedValueGuard.apply("phone", KEY, FORMAT, "13800138000", "", true, false));

        assertEquals("VALIDATION_FAILED", error.getCode());
        assertEquals("无权查看该字段，不能清空", error.getMessage());
    }

    @Test
    @DisplayName("清空：库中原值本来就为空 → 允许（返回提交的空串，不抛异常）")
    void clearingEmptyOriginalIsAllowed() {
        asUserWith("system:user:list");
        assertEquals("", MaskedValueGuard.apply("phone", KEY, FORMAT, null, "", true, false));
    }

    @Test
    @DisplayName("创建路径：出现掩码字符一律拒绝，防止掩码串被当新值入库")
    void rejectMaskedValueOnCreate() {
        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> MaskedValueGuard.rejectMaskedValue("phone", "138****8000"));

        assertEquals("INVALID_FORMAT", error.getCode());
        assertEquals("不允许提交掩码值", error.getMessage());
    }

    @Test
    @DisplayName("创建路径：正常值与 null 都放行")
    void rejectMaskedValueAllowsNormalInput() {
        MaskedValueGuard.rejectMaskedValue("phone", "13800138000");
        MaskedValueGuard.rejectMaskedValue("phone", null);
        MaskedValueGuard.rejectMaskedValue("phone", "");
    }
}
