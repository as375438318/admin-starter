package com.adminstarter.system.domain.storage;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 逻辑键规则：正式文件按 UTC 上传日期分片为 {@code yyyy/MM/dd/{sha256}}。
 * 分片临时键为 {@code .chunks/{uploadId}}，暂存键为 {@code .tmp/{uuid}}。
 */
public final class FileStorageKeys {

    public static final String TEMP_ROOT = ".tmp";
    public static final String CHUNK_ROOT = ".chunks";

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private FileStorageKeys() {
    }

    public static String forSha256(String sha256) {
        return LocalDate.now(ZoneOffset.UTC).format(DATE_PATH) + "/" + sha256;
    }

    public static String chunkRoot(String uploadId) {
        return CHUNK_ROOT + "/" + uploadId;
    }

    public static String chunkPart(String uploadId, int index) {
        return chunkRoot(uploadId) + "/" + index + ".part";
    }

    public static String temp(String uuid) {
        return TEMP_ROOT + "/" + uuid;
    }
}
