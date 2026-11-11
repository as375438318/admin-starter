package com.adminstarter.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 导入校验失败时 data 的结构：三个独立数字 + 截断后的错误清单。
 * <p>同一行可能有多个字段出错：{@code errorRows} 按行去重，{@code errorCount} 按字段错误计数，
 * 禁止用一个字段表达两种含义。{@code errors} 最多 200 条，{@code remainingErrors} 为未显示的剩余条数。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorData {

    private List<ValidationErrorData.FieldError> errors;
    private long totalRows;
    private long errorRows;
    private long errorCount;
    private long remainingErrors;
}
