package com.adminstarter.common.error;

import lombok.Getter;

/**
 * 业务层文件大小超限：HTTP 413，响应 data 中携带当前真实上限 maxSize（字节）。
 */
@Getter
public class PayloadTooLargeException extends RuntimeException {

    private final long maxSize;

    public PayloadTooLargeException(long maxSize, String message) {
        super(message);
        this.maxSize = maxSize;
    }
}
