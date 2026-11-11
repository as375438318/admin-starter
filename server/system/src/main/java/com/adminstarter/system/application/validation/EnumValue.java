package com.adminstarter.system.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字符串取值必须命中指定枚举的名称集合，大小写敏感。
 * 用于权限等相关字段的取值白名单：枚举常量增删即同步收紧/放宽，避免硬编码字符串集合。
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValueValidator.class)
public @interface EnumValue {

    Class<? extends Enum<?>> enumClass();

    String message() default "取值不合法";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
