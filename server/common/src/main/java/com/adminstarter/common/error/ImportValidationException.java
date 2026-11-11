package com.adminstarter.common.error;

import com.adminstarter.common.api.ImportErrorData;
import lombok.Getter;

/**
 * 导入校验失败：整批不落库，由全局异常处理返回 422 与逐行错误清单。
 */
@Getter
public class ImportValidationException extends RuntimeException {

    private final transient ImportErrorData data;

    public ImportValidationException(ImportErrorData data) {
        super("导入校验失败，共 " + data.getErrorCount() + " 处错误");
        this.data = data;
    }
}
