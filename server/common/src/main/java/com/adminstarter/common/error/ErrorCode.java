package com.adminstarter.common.error;

import lombok.Getter;

/**
 * 业务错误码。基础码值固定，禁止改写，见 CT-ERR-011；新增码只允许落在对应分段内。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok", 200),

    INVALID_PARAMETER(10001, "请求参数不合法", 400),
    PAYLOAD_TOO_LARGE(10002, "请求体过大", 413),

    UNAUTHORIZED(20101, "未登录或登录已过期", 401),
    TOKEN_EXPIRED(20102, "登录状态已过期", 401),
    ACCOUNT_DISABLED(20103, "账号已被停用", 401),
    ACCOUNT_DELETED(20104, "账号已被删除", 401),
    TOKEN_REVOKED(20105, "登录状态已失效，请重新登录", 401),
    PASSWORD_RESET(20106, "密码已被重置，请重新登录", 401),
    PASSWORD_CHANGED(20107, "密码已修改，请重新登录", 401),
    SESSION_KICKED(20108, "已被管理员下线，请重新登录", 401),
    FORBIDDEN(20301, "没有操作权限", 403),
    RESOURCE_NOT_FOUND(20401, "资源不存在", 404),
    RATE_LIMITED(20501, "请求过于频繁，请稍后再试", 429),

    VALIDATION_FAILED(30201, "参数校验失败", 422),
    PASSWORD_TOO_WEAK(30202, "密码强度不足", 422),

    BUSINESS_ERROR(40001, "业务处理失败", 400),
    STATE_CONFLICT(40901, "当前状态不允许该操作", 409),

    SYSTEM_ERROR(50001, "系统繁忙，请稍后重试", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
