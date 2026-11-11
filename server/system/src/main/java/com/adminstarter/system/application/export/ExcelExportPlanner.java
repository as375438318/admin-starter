package com.adminstarter.system.application.export;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.log.OperLogContext;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.excel.ExcelExportWriter;
import com.adminstarter.system.application.excel.ExcelSchema;
import com.adminstarter.system.domain.model.ExportTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * 通用导出编排：先按行数决定通道，再复用同一 schema、同一写表器。
 * <ul>
 *   <li>≤ {@value #SYNC_MAX_ROWS} 行：本次请求直接返回 xlsx 字节。</li>
 *   <li>＞ {@value #SYNC_MAX_ROWS} 行：建异步任务，返回任务 ID。</li>
 *   <li>＞ {@value #MAX_EXPORT_ROWS} 行：拒绝，提示缩小范围。</li>
 * </ul>
 * 业务模块只提供计数、同步取数、异步查询快照与文件名前缀。
 */
@Service
@RequiredArgsConstructor
public class ExcelExportPlanner {

    public static final int SYNC_MAX_ROWS = 2000;
    public static final int MAX_EXPORT_ROWS = 100000;
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("Asia/Shanghai"));

    private final ExcelExportWriter exportWriter;
    private final ExportTaskService exportTaskService;
    private final ExportTaskExecutor exportTaskExecutor;

    public <T> ExportPlan plan(ExcelSchema<T> schema, String fileNamePrefix, long totalRows,
                               String queryParams, Supplier<List<T>> syncRows) {
        if (totalRows > MAX_EXPORT_ROWS) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "导出数据超过 " + MAX_EXPORT_ROWS + " 行上限，请缩小筛选范围");
        }
        CurrentUser current = DataScopeContext.get();
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        String fileName = fileNamePrefix + "_" + FILE_TIME.format(Instant.now()) + ".xlsx";
        OperLogContext.put("fileName", fileName);
        OperLogContext.put("totalRows", totalRows);

        if (totalRows <= SYNC_MAX_ROWS) {
            List<T> rows = syncRows.get();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                exportWriter.writeSync(schema, rows, output);
                OperLogContext.put("mode", "SYNC");
                return new ExportPlan(output.toByteArray(), fileName, null, totalRows);
            } catch (IOException exception) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "导出文件生成失败");
            }
        }

        ExportTask task = exportTaskService.create(current.getUserId(), current.getUsername(),
                schema.key(), fileName, queryParams, totalRows);
        exportTaskExecutor.submit(task.getId());
        OperLogContext.put("mode", "ASYNC");
        OperLogContext.put("taskId", task.getId());
        return new ExportPlan(null, fileName, task.getId(), totalRows);
    }

    public record ExportPlan(byte[] content, String fileName, Long taskId, long totalRows) {

        public boolean sync() {
            return content != null;
        }
    }
}
