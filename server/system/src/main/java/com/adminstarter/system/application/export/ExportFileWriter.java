package com.adminstarter.system.application.export;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 异步导出写入钩子：业务模块按模块标识注册一个实现，负责重建查询并分页取数。
 * <p>写表、进度、取消、失败处理由 {@link ExportTaskExecutor} 统一负责，业务实现不得另写。</p>
 */
public interface ExportFileWriter {

    /** 与 {@code ExcelSchema#key()} 一致，用于任务分发。 */
    String moduleKey();

    /**
     * 流式写临时文件。
     *
     * @param queryParams 导出条件快照（JSON）
     * @param totalRows   预计行数，用于进度百分比
     */
    long write(Path target, String queryParams, long totalRows, ProgressReporter reporter) throws IOException;

    interface ProgressReporter {
        void onProgress(long written, long total);

        boolean canceled();
    }
}
