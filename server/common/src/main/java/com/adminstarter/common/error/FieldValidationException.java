package com.adminstarter.common.error;

import lombok.Getter;

/**
 * 字段级校验失败：由业务逻辑在保存前主动校验，响应结构与 Bean Validation 一致，
 * 统一返回 422 与 data.errors[{field, code, message}]。
 */
@Getter
public class FieldValidationException extends RuntimeException {

    private final String field;
    private final String code;

    public FieldValidationException(String field, String code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }
}
