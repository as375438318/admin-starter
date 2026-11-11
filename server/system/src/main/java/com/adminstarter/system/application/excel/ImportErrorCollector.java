package com.adminstarter.system.application.excel;

import com.adminstarter.common.api.ImportErrorData;
import com.adminstarter.common.api.ValidationErrorData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 导入错误汇总：同时维护「出错行数」与「字段级错误条数」两个不同口径。
 * <p>返回清单最多 {@value #MAX_RETURNED} 条，超出仅计数；剩余条数由
 * {@link #toData(long)} 计算。</p>
 */
public class ImportErrorCollector {

    public static final int MAX_RETURNED = 200;

    private final List<ValidationErrorData.FieldError> errors = new ArrayList<>();
    private final Set<Integer> errorRows = new HashSet<>();
    private long errorCount;

    public void add(int rowNumber, String fieldLabel, String code, String message) {
        errorCount++;
        errorRows.add(rowNumber);
        if (errors.size() < MAX_RETURNED) {
            errors.add(new ValidationErrorData.FieldError("第 " + rowNumber + " 行 · " + fieldLabel, code, message));
        }
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    public long errorCount() {
        return errorCount;
    }

    public ImportErrorData toData(long totalRows) {
        return new ImportErrorData(List.copyOf(errors), totalRows, errorRows.size(), errorCount,
                errorCount - errors.size());
    }
}
