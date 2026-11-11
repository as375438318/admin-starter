package com.adminstarter.common.error;

import lombok.Getter;

/**
 * 业务异常：只携带契约业务码，由全局异常处理统一映射为 HTTP 状态。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
