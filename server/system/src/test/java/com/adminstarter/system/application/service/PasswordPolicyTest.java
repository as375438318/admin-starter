package com.adminstarter.system.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.infrastructure.config.PasswordPolicyProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 口令策略单测（等保 P0-1 的验收口径）。
 *
 * <p>断言重点不是"能拒绝"，而是**拒绝时的具体原因**：只回"密码太简单"会让用户反复试错，
 * 所以每条用例都校验异常里的 message 指向真正缺的那一项。</p>
 */
class PasswordPolicyTest {

    private final PasswordPolicyProperties properties = new PasswordPolicyProperties();
    private final PasswordPolicy policy = new PasswordPolicy(properties);

    private String reasonOf(Runnable action) {
        try {
            action.run();
            return "<未抛异常>";
        } catch (BizException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_TOO_WEAK);
            return e.getMessage();
        }
    }

    @Test
    @DisplayName("合法口令：长度、字符类、无弱口令特征时通过")
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("Xk7#mQ2vLp9w", "zhangsan")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("Str0ng!Passwd", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("空口令被拒")
    void rejectsBlank() {
        assertThat(reasonOf(() -> policy.validate("   ", "zhangsan"))).contains("不能为空");
    }

    @Test
    @DisplayName("长度不足被拒，且提示最小长度")
    void rejectsTooShort() {
        assertThat(reasonOf(() -> policy.validate("Ab3#567", "zhangsan"))).contains("至少 10 位");
    }

    @Test
    @DisplayName("超长被拒（上界是明确的，不是隐含的）")
    void rejectsTooLong() {
        String longPassword = "Aa1!" + "x".repeat(properties.getMaxLength());
        assertThat(reasonOf(() -> policy.validate(longPassword, "zhangsan"))).contains("不能超过");
    }

    @Test
    @DisplayName("字符类不足 3 类被拒（本例：只有小写+数字）")
    void rejectsTooFewCharacterClasses() {
        assertThat(reasonOf(() -> policy.validate("abcdefg1234", "zhangsan"))).contains("至少 3 类");
    }

    @Test
    @DisplayName("默认初始口令 admin123 属弱口令被拒")
    void rejectsInitialAdminPassword() {
        assertThat(reasonOf(() -> policy.validate("admin123", "admin"))).contains("弱口令");
    }

    @Test
    @DisplayName("弱口令比较忽略大小写（Admin@123 同样被拒）")
    void rejectsWeakPasswordCaseInsensitive() {
        assertThat(reasonOf(() -> policy.validate("Admin@123", "someone"))).contains("弱口令");
    }

    @Test
    @DisplayName("口令包含账号名被拒")
    void rejectsUsernameInsidePassword() {
        assertThat(reasonOf(() -> policy.validate("Zhangsan#2026", "zhangsan"))).contains("不能包含账号名");
    }

    @Test
    @DisplayName("账号名短于 3 位时不参与包含判定（否则 'ab' 会误伤大量口令）")
    void ignoresVeryShortUsername() {
        assertThatCode(() -> policy.validate("Ab#kq7Lm2w", "ab")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("3 个及以上连续相同字符被拒")
    void rejectsRepeatedRun() {
        assertThat(reasonOf(() -> policy.validate("Aaaa#kq7Lm2", "zhangsan"))).contains("连续相同字符");
    }

    @Test
    @DisplayName("4 位连续顺序字符被拒（abcd / 1234）")
    void rejectsSequentialRun() {
        assertThat(reasonOf(() -> policy.validate("Xy#abcd1234", "zhangsan"))).contains("连续顺序字符");
    }

    @Test
    @DisplayName("口令过期判定：null 视为过期（fail-closed，历史数据不能假设刚改过）")
    void nullUpdatedAtCountsAsExpired() {
        assertThat(policy.isExpired(null, LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("口令有效期边界：到期日 +expireDays 之后才算过期")
    void expiryBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);
        LocalDateTime justChanged = now.minusDays(properties.getExpireDays() - 1);
        LocalDateTime expired = now.minusDays(properties.getExpireDays() + 1);
        assertThat(policy.isExpired(justChanged, now)).isFalse();
        assertThat(policy.isExpired(expired, now)).isTrue();
    }

    @Test
    @DisplayName("可关闭连续字符校验（配置为 false 时同样口令通过）")
    void sequentialCheckCanBeDisabled() {
        PasswordPolicyProperties lenient = new PasswordPolicyProperties();
        lenient.setForbidSequentialChars(false);
        lenient.setForbidRepeatedChars(false);
        PasswordPolicy lenientPolicy = new PasswordPolicy(lenient);
        assertThatCode(() -> lenientPolicy.validate("Xy#abcd1234", "zhangsan")).doesNotThrowAnyException();
    }
}
