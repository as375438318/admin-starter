package com.adminstarter.system.application.export;

import com.adminstarter.system.application.excel.ExcelExportWriter;
import com.adminstarter.system.application.excel.demo.DemoTaskExcelSchema;
import com.adminstarter.system.application.excel.demo.DemoTaskExportRow;
import com.adminstarter.system.application.excel.demo.DemoTaskExportRowAssembler;
import com.adminstarter.system.domain.model.DemoTask;
import com.adminstarter.system.domain.query.DemoTaskQuery;
import com.adminstarter.system.domain.repository.DemoTaskRepository;
import com.adminstarter.system.infrastructure.persistence.DataPermissionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 导出文件写入：分页读取（每页 2000 行）+ SXSSF 流式写，内存不随总行数增长。
 * <p>导出数据必须进入 {@link DataPermissionContext} 受控查询；脱敏由
 * {@link ExcelExportWriter} 内的 MaskResolver 统一处理。</p>
 */
@Component
@RequiredArgsConstructor
public class DemoTaskExportFileWriter implements ExportFileWriter {

    public static final int PAGE_SIZE = 2000;

    private final DemoTaskRepository demoTaskRepository;
    private final DemoTaskExportRowAssembler rowAssembler;
    private final DemoTaskExcelSchema schema;
    private final ExcelExportWriter exportWriter;
    private final ObjectMapper objectMapper;

    @Override
    public String moduleKey() {
        return schema.key();
    }

    @Override
    public long write(Path target, String queryParams, long totalRows, ProgressReporter reporter) throws IOException {
        DemoTaskQuery query = objectMapper.readValue(queryParams, DemoTaskQuery.class);
        Files.createDirectories(target.getParent());
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            return exportWriter.writeStreaming(schema, new ExcelExportWriter.RowSource<>() {
                private int offset;

                @Override
                public List<DemoTaskExportRow> next() {
                    if (reporter != null && reporter.canceled()) {
                        throw new ExportCanceledException();
                    }
                    if (offset >= totalRows) {
                        return null;
                    }
                    int currentOffset = offset;
                    List<DemoTask> page = DataPermissionContext.controlled(
                            () -> demoTaskRepository.findForExport(query, currentOffset, PAGE_SIZE));
                    if (page.isEmpty()) {
                        return null;
                    }
                    offset += page.size();
                    return rowAssembler.assemble(page);
                }
            }, output, written -> {
                if (reporter != null) {
                    reporter.onProgress(written, totalRows);
                }
            }, ExportFieldKeys.parse(query.getFields()));
        }
    }
}
