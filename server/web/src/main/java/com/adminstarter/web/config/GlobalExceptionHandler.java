package com.adminstarter.web.config;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.ImportErrorData;
import com.adminstarter.common.api.ValidationErrorData;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.error.FieldValidationException;
import com.adminstarter.common.error.ImportValidationException;
import com.adminstarter.common.error.PayloadTooLargeException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全局异常到统一信封与契约 HTTP 状态码的唯一映射处。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBiz(BizException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode.getCode(), exception.getMessage(), null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleValidation(BindException exception) {
        List<ValidationErrorData.FieldError> errors = new ArrayList<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationErrorData.FieldError(fieldError.getField(),
                    businessCode(fieldError.getCode()),
                    fieldError.getDefaultMessage()));
        }
        ValidationErrorData data = new ValidationErrorData(errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.getCode(),
                        ErrorCode.VALIDATION_FAILED.getMessage(), data));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleConstraint(ConstraintViolationException exception) {
        List<ValidationErrorData.FieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationErrorData.FieldError(violation.getPropertyPath().toString(),
                        businessCode(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()),
                        violation.getMessage()))
                .toList();
        ValidationErrorData data = new ValidationErrorData(errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.getCode(),
                        ErrorCode.VALIDATION_FAILED.getMessage(), data));
    }

    /**
     * 把校验框架的约束类型翻译成契约层字段级业务码，禁止把框架常量（如 Size/NotBlank）原样透出。
     * 必填类 → REQUIRED；格式类 → INVALID_FORMAT；长度类 → TOO_LONG。
     * 空白串只命中必填类，不会落到 INVALID_FORMAT。
     * 契约未定义的其他约束类型统一兜底为 VALIDATION_FAILED。
     */
    private static String businessCode(String constraintCode) {
        if (constraintCode == null) {
            return "VALIDATION_FAILED";
        }
        return switch (constraintCode) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "Size" -> "TOO_LONG";
            default -> "VALIDATION_FAILED";
        };
    }

    /**
     * 请求参数格式类错误统一 400。
     * 覆盖：类型不匹配、缺少必填 query/part、方法不支持、请求体无法解析（JSON 语法错/空体）、
     * Content-Type 不支持、multipart 报文畸形。
     * 未处理前 HttpMessageNotReadableException 等会落到兜底 500，违反 CT-HTTP-003 与 BE-CON-010。
     * 响应文案固定取 INVALID_PARAMETER，不透传异常信息（BE-CON-009）。
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, HttpRequestMethodNotSupportedException.class,
            HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class,
            MultipartException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception exception) {
        log.warn("请求参数格式错误：{} - {}", exception.getClass().getSimpleName(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.INVALID_PARAMETER.getCode(),
                        ErrorCode.INVALID_PARAMETER.getMessage(), null));
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorData>> handleFieldValidation(FieldValidationException exception) {
        ValidationErrorData data = new ValidationErrorData(List.of(
                new ValidationErrorData.FieldError(exception.getField(), exception.getCode(), exception.getMessage())));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.getCode(),
                        ErrorCode.VALIDATION_FAILED.getMessage(), data));
    }

    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<ApiResponse<ImportErrorData>> handleImportValidation(ImportValidationException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_FAILED.getCode(),
                        exception.getMessage(), exception.getData()));
    }

    /**
     * 业务层上传上限：HTTP 413，message 写明当前真实上限，data.maxSize 供前端换算展示。
     * 容器层天花板（Multipart 200MB）另有 MaxUploadSizeExceededException 兜底。
     */
    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBizPayloadTooLarge(PayloadTooLargeException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(ErrorCode.PAYLOAD_TOO_LARGE.getCode(),
                        exception.getMessage(), Map.of("maxSize", exception.getMaxSize())));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handlePayloadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail(ErrorCode.PAYLOAD_TOO_LARGE.getCode(),
                        ErrorCode.PAYLOAD_TOO_LARGE.getMessage(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage(), null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                        ErrorCode.RESOURCE_NOT_FOUND.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnknown(Exception exception) {
        log.error("未处理异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), null));
    }
}
