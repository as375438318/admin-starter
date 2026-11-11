package com.adminstarter.system.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 对 {@link EnumValue} 的校验实现：按枚举常量名精确匹配，不做 trim、不做大小写转换（CT-FMT-008）。
 * null/空串交由 @NotBlank 处理，本校验器不重复判定。
 */
public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private Class<? extends Enum<?>> enumClass;

    @Override
    public void initialize(EnumValue annotation) {
        this.enumClass = annotation.enumClass();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
