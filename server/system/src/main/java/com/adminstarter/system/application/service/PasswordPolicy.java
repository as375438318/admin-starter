package com.adminstarter.system.application.service;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.infrastructure.config.PasswordPolicyProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 口令复杂度策略（等保 2.0「身份鉴别·口令复杂度与定期更换」，见 docs/compliance/dengbao-breakdown.md P0-1）。
 *
 * <p>失败一律抛 {@link ErrorCode#PASSWORD_TOO_WEAK} 并给出**具体原因**——只回"密码太简单"
 * 会让用户反复试错；说清缺哪一类字符/为什么被拒，才能一次改对。</p>
 *
 * <p>弱口令表覆盖：默认初始口令（admin123）、常见弱口令、以及"用户名本身/用户名+简单后缀"。
 * 这不是完备的字典攻击防护（那需要外部字典/泄露库），定位是**拦住最常见的错**。</p>
 */
@Component
@RequiredArgsConstructor
public class PasswordPolicy {

    /** 常见弱口令。比较时统一转小写，因此表内全部小写。 */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "admin", "admin123", "admin@123", "administrator", "root", "root123",
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "111111", "000000", "888888", "666666", "123123", "abc123", "abcd1234",
            "password", "password1", "passw0rd", "p@ssw0rd", "qwerty", "qwerty123",
            "1qaz2wsx", "zaq12wsx", "iloveyou", "letmein", "welcome", "monkey",
            "admin888", "adminadmin", "test1234", "admin-starter");

    private final PasswordPolicyProperties properties;

    /**
     * 校验口令强度。不通过时抛 {@link BizException}（422 + 30202）。
     *
     * @param rawPassword 明文口令（尚未加密）
     * @param username    账号名，用于"口令不得包含账号名"判定；可为 null（如重置场景拿不到）
     */
    public void validate(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "密码不能为空");
        }
        // 弱口令优先于长度判定：像 admin123 这种，说"属于常见弱口令"比说"长度不足"更贴近事实，
        // 否则用户会以为只要凑够长度就行。
        String lower = rawPassword.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "该密码属于常见弱口令，请更换");
        }

        int min = properties.getMinLength();
        if (rawPassword.length() < min) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK,
                    "密码长度至少 " + min + " 位");
        }
        if (rawPassword.length() > properties.getMaxLength()) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK,
                    "密码长度不能超过 " + properties.getMaxLength() + " 位");
        }

        int classes = characterClasses(rawPassword);
        if (classes < properties.getMinCharacterClasses()) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK,
                    "密码需包含大写字母、小写字母、数字、特殊字符中的至少 "
                            + properties.getMinCharacterClasses() + " 类");
        }

        if (username != null && !username.isBlank()) {
            String name = username.toLowerCase(Locale.ROOT);
            if (name.length() >= 3 && lower.contains(name)) {
                throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "密码不能包含账号名");
            }
        }

        if (properties.isForbidRepeatedChars() && hasRepeatedRun(rawPassword, 3)) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "密码不能包含 3 个及以上连续相同字符");
        }
        if (properties.isForbidSequentialChars() && hasSequentialRun(lower, 4)) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "密码不能包含 4 位及以上连续顺序字符");
        }
    }

    /**
     * 口令是否已过期。{@code passwordUpdatedAt} 为 null 时按"过期"处理（历史数据没有记录，
     * 不能假设它刚改过——fail-closed）。
     */
    public boolean isExpired(LocalDateTime passwordUpdatedAt, LocalDateTime now) {
        if (passwordUpdatedAt == null) {
            return true;
        }
        return passwordUpdatedAt.plusDays(properties.getExpireDays()).isBefore(now);
    }

    private int characterClasses(String raw) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char c : raw.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                special = true;
            }
        }
        int count = 0;
        for (boolean present : new boolean[] {upper, lower, digit, special}) {
            if (present) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRepeatedRun(String raw, int run) {
        int streak = 1;
        for (int i = 1; i < raw.length(); i++) {
            streak = raw.charAt(i) == raw.charAt(i - 1) ? streak + 1 : 1;
            if (streak >= run) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSequentialRun(String lower, int run) {
        int streak = 1;
        for (int i = 1; i < lower.length(); i++) {
            streak = lower.charAt(i) - lower.charAt(i - 1) == 1 ? streak + 1 : 1;
            if (streak >= run) {
                return true;
            }
        }
        return false;
    }
}
