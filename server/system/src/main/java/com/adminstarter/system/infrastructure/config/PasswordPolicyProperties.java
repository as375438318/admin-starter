package com.adminstarter.system.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 口令策略配置。台账见 docs/design/config-registry.yaml（键：app.security.password.*）。
 *
 * <p>默认值即"等保 P0-1 验收口径"：长度 ≥ 10、至少 3 类字符、90 天过期。
 * 改小这些值等于降低安全基线，必须在变更记录里写明理由。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.password")
public class PasswordPolicyProperties {

    /** 最短长度。 */
    private int minLength = 10;

    /** 最长长度（防止超长串拖慢 BCrypt，同时是明确的上界）。 */
    private int maxLength = 64;

    /** 至少包含几类字符（大写/小写/数字/特殊）。 */
    private int minCharacterClasses = 3;

    /** 口令有效期（天）；到期后登录会要求修改。 */
    private int expireDays = 90;

    /** 禁止 3 个及以上连续相同字符。 */
    private boolean forbidRepeatedChars = true;

    /** 禁止 4 位及以上连续顺序字符（如 abcd、1234）。 */
    private boolean forbidSequentialChars = true;
}
