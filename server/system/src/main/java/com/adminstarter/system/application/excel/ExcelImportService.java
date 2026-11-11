package com.adminstarter.system.application.excel;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.error.ImportValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通用导入流水线：SAX 解析 → 表头核对 → 元数据校验 → 业务校验 → 整批写入。
 * <p>任一行有错即抛 {@link ImportValidationException}，事务回滚，整批不落库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ExcelStreamParser streamParser;
    private final ExcelRowValidator rowValidator;
    private final ReferenceDirectoryService referenceDirectoryService;

    @Transactional(rollbackFor = Exception.class)
    public <T, C> ImportOutcome importFile(InputStream input, ExcelImportHandler<T, C> handler, int maxRows) {
        long start = System.nanoTime();
        ExcelSchema<?> schema = handler.schema();
        ExcelStreamParser.ParseResult parsed = streamParser.parse(input, schema, maxRows);
        verifyHeader(schema, parsed.header());
        long parsedAt = System.nanoTime();

        ImportErrorCollector errors = new ImportErrorCollector();
        ReferenceDirectoryService.ReferenceContext refs = referenceDirectoryService.context(schema.importFields());
        long refsAt = System.nanoTime();
        List<Map<String, Object>> valuesList = new ArrayList<>(parsed.rows().size());
        for (RawRow row : parsed.rows()) {
            valuesList.add(rowValidator.validate(schema, row, errors, refs));
        }
        long validatedAt = System.nanoTime();

        C context = handler.prepare(parsed.rows());
        for (int index = 0; index < parsed.rows().size(); index++) {
            handler.validate(parsed.rows().get(index), valuesList.get(index), context, errors);
        }
        long businessAt = System.nanoTime();

        if (errors.hasErrors()) {
            log.info("excel-import phases rows={} parse={}ms refs={}ms validate={}ms business={}ms result=REJECTED errors={}",
                    parsed.rows().size(), millis(start, parsedAt), millis(parsedAt, refsAt),
                    millis(refsAt, validatedAt), millis(validatedAt, businessAt), errors.errorCount());
            throw new ImportValidationException(errors.toData(parsed.rows().size()));
        }

        List<T> entities = new ArrayList<>(parsed.rows().size());
        for (Map<String, Object> values : valuesList) {
            entities.add(handler.toEntity(values));
        }
        long entitiesAt = System.nanoTime();
        handler.persist(entities);
        long persistedAt = System.nanoTime();
        log.info("excel-import phases rows={} parse={}ms refs={}ms validate={}ms business={}ms entity={}ms persist={}ms total={}ms result=OK",
                parsed.rows().size(), millis(start, parsedAt), millis(parsedAt, refsAt),
                millis(refsAt, validatedAt), millis(validatedAt, businessAt),
                millis(businessAt, entitiesAt), millis(entitiesAt, persistedAt), millis(start, persistedAt));
        return new ImportOutcome(parsed.rows().size(), entities.size());
    }

    private long millis(long from, long to) {
        return TimeUnit.NANOSECONDS.toMillis(to - from);
    }

    /**
     * 表头核对：模板要求的列必须全部存在，按列名读取，允许导出文件带着导出专用列直接回导，
     * 形成「导出 → 修改 → 导入」闭环。
     */
    private void verifyHeader(ExcelSchema<?> schema, List<String> actual) {
        List<String> expected = schema.importFields().stream()
                .map(ExcelField::getImportHeader)
                .toList();
        List<String> missing = expected.stream().filter(header -> !actual.contains(header)).toList();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "表头与模板不一致，缺少列：" + String.join("、", missing) + "；请重新下载模板填写");
        }
    }
}
