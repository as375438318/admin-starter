package com.adminstarter.system.application.excel.post;

import com.adminstarter.system.application.excel.ExcelExportWriter;
import com.adminstarter.system.application.export.ExportCanceledException;
import com.adminstarter.system.application.export.ExportFileWriter;
import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.domain.query.PostQuery;
import com.adminstarter.system.domain.repository.PostRepository;
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
 * 岗位导出写入：分页读取 + 流式写 xlsx，复用岗位列表查询条件。
 */
@Component
@RequiredArgsConstructor
public class PostExportFileWriter implements ExportFileWriter {

    public static final int PAGE_SIZE = 2000;

    private final PostRepository postRepository;
    private final PostExportRowAssembler rowAssembler;
    private final PostExcelSchema schema;
    private final ExcelExportWriter exportWriter;
    private final ObjectMapper objectMapper;

    @Override
    public String moduleKey() {
        return schema.key();
    }

    @Override
    public long write(Path target, String queryParams, long totalRows, ProgressReporter reporter) throws IOException {
        PostQuery query = objectMapper.readValue(queryParams, PostQuery.class);
        Files.createDirectories(target.getParent());
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            return exportWriter.writeStreaming(schema, new ExcelExportWriter.RowSource<>() {
                private int offset;

                @Override
                public List<PostExportRow> next() {
                    if (reporter != null && reporter.canceled()) {
                        throw new ExportCanceledException();
                    }
                    if (offset >= totalRows) {
                        return null;
                    }
                    List<Post> page = postRepository.findForExport(query, offset, PAGE_SIZE);
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
            });
        }
    }
}
