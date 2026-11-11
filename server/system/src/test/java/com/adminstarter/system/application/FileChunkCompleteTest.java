package com.adminstarter.system.application;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.dto.FileDtos;
import com.adminstarter.system.domain.model.FileChunkUpload;
import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.system.domain.repository.FileChunkUploadRepository;
import com.adminstarter.system.domain.repository.FileRepository;
import com.adminstarter.system.domain.storage.FileStorageKeys;
import com.adminstarter.system.infrastructure.storage.LocalFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片合并收尾：完整性、整体校验、去重落库、临时分片清理。
 * <p>合并是"最后一道闸"：分片可以一个个传上来，但只有这里会确认<b>拼起来的东西就是用户声明的那个文件</b>。
 * 少一道校验，用户拿到的就是损坏文件；忘了清理分片目录，磁盘会被临时片慢慢吃满。</p>
 */
class FileChunkCompleteTest {

    @TempDir
    Path tempDir;

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FileChunkUploadRepository chunkUploadRepository = mock(FileChunkUploadRepository.class);
    private final PublicConfigAppService publicConfigAppService = mock(PublicConfigAppService.class);

    private LocalFileStorage storage;
    private FileAppService service;

    private static final String UPLOAD_ID = "session1";
    private static final String PART_A = "hello-";
    private static final String PART_B = "world";

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage();
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        storage.init();

        service = new FileAppService(fileRepository, chunkUploadRepository, publicConfigAppService, storage);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "allowedExtensions", "pdf,txt,zip");
        service.init();

        when(publicConfigAppService.uploadMaxSize()).thenReturn(10L * 1024 * 1024);
        DataScopeContext.set(CurrentUser.builder().userId(7L).username("tester").deptId(3L)
                .permissions(Set.of()).build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 建会话并把两片真实字节写进存储。 */
    private FileChunkUpload session(String declaredSha, long declaredSize, String status, String uploaded) {
        FileChunkUpload session = new FileChunkUpload();
        session.setUploadId(UPLOAD_ID);
        session.setUploaderId(7L);
        session.setStatus(status);
        session.setOriginalName("合并结果.txt");
        session.setContentType("text/plain");
        session.setSha256(declaredSha);
        session.setSize(declaredSize);
        session.setChunkSize(1024);
        session.setTotalChunks(2);
        session.setUploadedChunks(uploaded);
        when(chunkUploadRepository.findByUploadId(UPLOAD_ID)).thenReturn(session);
        return session;
    }

    private void storePart(int index, String content) throws IOException {
        Path source = tempDir.resolve("part-" + index + ".tmp");
        Files.writeString(source, content);
        storage.store(source, FileStorageKeys.chunkPart(UPLOAD_ID, index));
    }

    @Test
    @DisplayName("会话状态不是 UPLOADING → 冲突，不重复合并")
    void nonUploadingSessionIsRejected() {
        session(sha256(PART_A + PART_B), 11, "MERGED", "0,1");

        BizException error = assertThrows(BizException.class, () -> service.completeChunkSession(UPLOAD_ID));

        assertEquals(ErrorCode.STATE_CONFLICT, error.getErrorCode());
        verify(fileRepository, never()).insert(any());
    }

    @Test
    @DisplayName("分片不完整 → 提示缺少第几片（用户知道该重传哪一片）")
    void missingChunkIsReported() {
        session(sha256(PART_A + PART_B), 11, "UPLOADING", "0");

        BizException error = assertThrows(BizException.class, () -> service.completeChunkSession(UPLOAD_ID));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("缺少第 1 片"), "实际：" + error.getMessage());
        verify(fileRepository, never()).insert(any());
    }

    @Test
    @DisplayName("合并后大小与声明不一致 → 拒绝（防止客户端少传/多传）")
    void sizeMismatchIsRejected() throws IOException {
        storePart(0, PART_A);
        storePart(1, PART_B);   // 实际 11 字节
        session(sha256(PART_A + PART_B), 99, "UPLOADING", "0,1");

        BizException error = assertThrows(BizException.class, () -> service.completeChunkSession(UPLOAD_ID));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("大小"));
        verify(fileRepository, never()).insert(any());
    }

    @Test
    @DisplayName("合并后整体 sha256 不匹配 → 拒绝并提示重新上传（内容被篡改或传错片）")
    void shaMismatchIsRejected() throws IOException {
        storePart(0, PART_A);
        storePart(1, PART_B);
        session(sha256("完全不同的内容"), 11, "UPLOADING", "0,1");

        BizException error = assertThrows(BizException.class, () -> service.completeChunkSession(UPLOAD_ID));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("请重新上传"));
        verify(fileRepository, never()).insert(any());
        verify(chunkUploadRepository, never()).updateStatus(anyString(), anyString());
    }

    @Test
    @DisplayName("合并成功 → 字节落盘、建记录、清理分片目录、会话置 MERGED")
    void successfulMergeStoresBytesAndCleansUp() throws IOException {
        storePart(0, PART_A);
        storePart(1, PART_B);
        session(sha256(PART_A + PART_B), 11, "UPLOADING", "0,1");

        FileDtos.Response response = service.completeChunkSession(UPLOAD_ID);

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).insert(captor.capture());
        FileRecord record = captor.getValue();

        assertEquals("合并结果.txt", record.getOriginalName());
        assertEquals(11L, record.getSize());
        assertEquals(sha256(PART_A + PART_B), record.getSha256());
        assertEquals(7L, record.getUploaderId());
        assertTrue(storage.exists(record.getStoragePath()), "合并后的字节必须在存储里");
        assertFalse(storage.exists(FileStorageKeys.chunkPart(UPLOAD_ID, 0)), "分片应被清理，否则磁盘被临时片吃满");
        assertEquals(record.getStoragePath().substring(record.getStoragePath().lastIndexOf('/') + 1),
                record.getStoredName());
        verify(chunkUploadRepository).updateStatus(UPLOAD_ID, "MERGED");
        assertFalse(Boolean.TRUE.equals(response.getInstant()));
    }

    @Test
    @DisplayName("合并成功但已有相同内容 → 复用存储路径并标记秒传（仍建新记录）")
    void successfulMergeDeduplicates() throws IOException {
        String content = PART_A + PART_B;
        FileRecord existing = new FileRecord();
        existing.setStoragePath("2026/01/01/existing.txt");
        Path source = tempDir.resolve("existing.tmp");
        Files.writeString(source, content);
        storage.store(source, existing.getStoragePath());
        when(fileRepository.findBySha(sha256(content), 11L)).thenReturn(existing);

        storePart(0, PART_A);
        storePart(1, PART_B);
        session(sha256(content), 11, "UPLOADING", "0,1");

        FileDtos.Response response = service.completeChunkSession(UPLOAD_ID);

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).insert(captor.capture());
        assertEquals(existing.getStoragePath(), captor.getValue().getStoragePath());
        assertTrue(Boolean.TRUE.equals(response.getInstant()));
    }

    @Test
    @DisplayName("放弃会话：清理分片目录并把 UPLOADING 置为 ABORTED")
    void abortCleansChunksAndMarksAborted() throws IOException {
        storePart(0, PART_A);
        session(sha256(PART_A + PART_B), 11, "UPLOADING", "0");

        service.abortChunkSession(UPLOAD_ID);

        assertFalse(storage.exists(FileStorageKeys.chunkPart(UPLOAD_ID, 0)));
        verify(chunkUploadRepository).updateStatus(UPLOAD_ID, "ABORTED");
    }

    @Test
    @DisplayName("放弃已完成会话：只清分片，不覆盖 MERGED 状态（避免把成功的记录标成放弃）")
    void abortKeepsTerminalStatus() throws IOException {
        storePart(0, PART_A);
        session(sha256(PART_A + PART_B), 11, "MERGED", "0,1");

        service.abortChunkSession(UPLOAD_ID);

        assertFalse(storage.exists(FileStorageKeys.chunkPart(UPLOAD_ID, 0)));
        verify(chunkUploadRepository, never()).updateStatus(anyString(), anyString());
    }

    @Test
    @DisplayName("合并后再次查询会话状态：MERGED 时不可继续上传（状态机不允许回退）")
    void mergedSessionCannotContinue() {
        session(sha256(PART_A + PART_B), 11, "MERGED", "0,1");

        BizException error = assertThrows(BizException.class, () -> service.uploadChunk(UPLOAD_ID, 0,
                new org.springframework.mock.web.MockMultipartFile("chunk", "p", "text/plain",
                        "x".getBytes(StandardCharsets.UTF_8))));

        assertEquals(ErrorCode.STATE_CONFLICT, error.getErrorCode());
        verify(chunkUploadRepository, never()).updateChunks(anyString(), anyString());
    }

    @Test
    @DisplayName("匿名调用合并 → 未登录拒绝（合并会建记录，必须有人可归属）")
    void anonymousMergeIsRejected() {
        DataScopeContext.clear();
        FileChunkUpload others = session(sha256(PART_A + PART_B), 11, "UPLOADING", "0,1");
        others.setUploaderId(99L);

        assertThrows(BizException.class, () -> service.completeChunkSession(UPLOAD_ID));
        assertNull(DataScopeContext.get());
    }
}
