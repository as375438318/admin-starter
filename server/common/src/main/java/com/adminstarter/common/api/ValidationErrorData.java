package com.adminstarter.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 校验失败时 data 的结构：data.errors[{field, code, message}]，见 CT-ERR-006。
 */
@Data
@AllArgsConstructor
public class ValidationErrorData {

    private List<FieldError> errors;

    @Data
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String code;
        private String message;
    }
}
