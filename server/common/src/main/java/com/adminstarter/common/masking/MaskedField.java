package com.adminstarter.common.masking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏标记：只允许标在响应 DTO 的字段上。
 * value 为字段标识（如 user.phone），与权限码拼装为 system:user.phone:view / :mask。
 * 新增业务敏感字段只需加本注解并配授权，不需要改框架代码。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaskedField {

    /** 字段标识，格式 资源.字段，例如 user.phone。 */
    String value();

    /** 掩码格式。 */
    MaskFormat format();
}
