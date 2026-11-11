package com.adminstarter.system.application.excel;

import com.adminstarter.common.cache.CacheNamespaces;
import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.log.OperLogContext;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 通用导入入口：文件门槛、短时去重、审计补充字段。校验与落库由 {@link ExcelImportService} 统一执行，
 * 业务模块只提供 {@link ExcelImportHandler}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportAppService {

    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    public static final int MAX_ROWS = 20000;

    /** 覆盖一次完整执行的最坏耗时（2 万行解析 + 校验 + 单事务写入），留足余量。 */
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    private final ExcelImportService excelImportService;
    private final CacheService cacheService;

    public <T, C> ImportOutcome importFile(MultipartFile file, ExcelImportHandler<T, C> handler) {
        long start = System.nanoTime();
        String fileName = validate(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件读取失败，请重试");
        }
        long readAt = System.nanoTime();
        OperLogContext.put("fileName", fileName);
        OperLogContext.put("fileSize", bytes.length);
        OperLogContext.put("mode", "SYNC");

        String dedupKey = dedupKey(handler.schema().key(), bytes);
        if (!cacheService.setIfAbsent(dedupKey, "1", DEDUP_TTL)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER,
                    "同一文件正在处理或刚刚提交过，请勿重复提交；如已修改内容请重新上传");
        }
        long dedupAt = System.nanoTime();
        try {
            ImportOutcome outcome = excelImportService.importFile(
                    new ByteArrayInputStream(bytes), handler, MAX_ROWS);
            OperLogContext.put("totalRows", outcome.totalRows());
            OperLogContext.put("importedRows", outcome.imported());
            log.info("excel-import-app size={}B read={}ms dedup={}ms pipeline={}ms total={}ms rows={} result=OK",
                    bytes.length, millis(start, readAt), millis(readAt, dedupAt),
                    millis(dedupAt, System.nanoTime()), millis(start, System.nanoTime()), outcome.totalRows());
            return outcome;
        } catch (RuntimeException exception) {
            cacheService.delete(dedupKey);
            log.info("excel-import-app size={}B read={}ms dedup={}ms pipeline={}ms total={}ms result=REJECTED",
                    bytes.length, millis(start, readAt), millis(readAt, dedupAt),
                    millis(dedupAt, System.nanoTime()), millis(start, System.nanoTime()));
            throw exception;
        }
    }

    private long millis(long from, long to) {
        return TimeUnit.NANOSECONDS.toMillis(to - from);
    }

    private String validate(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!fileName.toLowerCase().endsWith(".xlsx")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "只支持 .xlsx 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件不能超过 50MB");
        }
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "文件为空");
        }
        return fileName;
    }

    private String dedupKey(String moduleKey, byte[] bytes) {
        CurrentUser current = DataScopeContext.get();
        Long userId = current == null ? 0L : current.getUserId();
        return CacheNamespaces.IMPORT_DEDUP_PREFIX + moduleKey + ":" + userId + ":" + sha256(bytes);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件指纹计算失败");
        }
    }
}
