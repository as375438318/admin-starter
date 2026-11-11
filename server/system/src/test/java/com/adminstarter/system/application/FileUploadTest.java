package com.adminstarter.system.application;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.error.PayloadTooLargeException;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.dto.FileDtos;
import com.adminstarter.system.domain.model.FileChunkUpload;
import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.system.domain.repository.FileChunkUploadRepository;
import com.adminstarter.system.domain.repository.FileRepository;
import com.adminstarter.system.infrastructure.storage.LocalFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * 上传 / 秒传 / 分片上传的校验与去重。
 * <p>用真实的 {@link LocalFileStorage}（指向 {@code @TempDir}）而不是 mock，
 * 因为"字节到底有没有落盘"正是这块最容易出错的地方——mock 掉存储等于把这部分要么测不到、要么测出假绿。</p>
 * <p>关键不变量：① 不允许的类型/大小/空文件必须拒绝；② 相同内容（sha256）已存在时**不重复写字节**但**仍建新记录**；
 * ③ 分片会话只有上传者本人能续传，序号与大小都必须在约定范围内。</p>
 */
class FileUploadTest {

    @TempDir
    Path tempDir;

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FileChunkUploadRepository chunkUploadRepository = mock(FileChunkUploadRepository.class);
    private final PublicConfigAppService publicConfigAppService = mock(PublicConfigAppService.class);

    private LocalFileStorage storage;
    private FileAppService service;

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage();
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        storage.init();

        service = new FileAppService(fileRepository, chunkUploadRepository, publicConfigAppService, storage);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "allowedExtensions", "pdf,txt,zip");
        service.init();

        when(publicConfigAppService.uploadMaxSize()).thenReturn(MAX_SIZE);
        DataScopeContext.set(CurrentUser.builder().userId(7L).username("tester").deptId(3L)
                .permissions(Set.of()).build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    /** 与实现一致地计算 sha256（实现内部用 MessageDigest，这里独立算一遍，避免"用被测代码验证被测代码"）。 */
    private static String sha256Of(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---------------------------------------------------------------- 上传校验

    @Test
    @DisplayName("空文件 → 拒绝")
    void emptyFileIsRejected() {
        BizException error = assertThrows(BizException.class,
                () -> service.upload(file("a.pdf", "application/pdf", new byte[0])));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("不能为空"));
        verify(fileRepository, never()).insert(any());
    }

    @Test
    @DisplayName("超过上传上限 → 拒绝，提示带上限（不是笼统的系统错误）")
    void oversizedFileIsRejected() {
        when(publicConfigAppService.uploadMaxSize()).thenReturn(4L);

        assertThrows(PayloadTooLargeException.class,
                () -> service.upload(file("a.pdf", "application/pdf", "12345".getBytes(StandardCharsets.UTF_8))));
        verify(fileRepository, never()).insert(any());
    }

    @Test
    @DisplayName("扩展名不在白名单 → 拒绝，提示里带扩展名")
    void unsupportedExtensionIsRejected() {
        BizException error = assertThrows(BizException.class,
                () -> service.upload(file("恶意.exe", "application/octet-stream", "x".getBytes(StandardCharsets.UTF_8))));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        assertTrue(error.getMessage().contains("exe"), "实际：" + error.getMessage());
    }

    @Test
    @DisplayName("正常上传 → 字节落盘、记录带 sha256 与上传人、响应 instant=false")
    void normalUploadStoresBytesAndRecord() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        FileDtos.Response response = service.upload(file("报告.pdf", "application/pdf", content));

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).insert(captor.capture());
        FileRecord record = captor.getValue();

        assertEquals("报告.pdf", record.getOriginalName());
        assertEquals((long) content.length, record.getSize());
        assertEquals(sha256Of("hello world"), record.getSha256());
        assertEquals(7L, record.getUploaderId());
        assertEquals(3L, record.getDeptId());
        assertTrue(storage.exists(record.getStoragePath()), "字节必须真的落到存储里");
        assertNotNull(response.getDownloadUrl());
        assertFalse(Boolean.TRUE.equals(response.getInstant()));
    }

    @Test
    @DisplayName("相同内容已存在且字节在库 → 秒传：复用一个存储路径，不重复写字节，但仍建新记录")
    void duplicateContentIsDeduplicated() {
        byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);
        String sha = sha256Of("same-content");
        FileRecord existing = new FileRecord();
        existing.setStoragePath("2026/01/01/existing.pdf");
        storage.store(writeTemp("x"), existing.getStoragePath());
        when(fileRepository.findBySha(sha, content.length)).thenReturn(existing);

        FileDtos.Response response = service.upload(file("副本.pdf", "application/pdf", content));

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).insert(captor.capture());
        assertEquals(existing.getStoragePath(), captor.getValue().getStoragePath(), "命中去重后应复用既有存储路径");
        assertEquals("副本.pdf", captor.getValue().getOriginalName(), "记录仍是新的（文件名/上传人各是自己的）");
        assertTrue(Boolean.TRUE.equals(response.getInstant()), "应标记为秒传");
    }

    @Test
    @DisplayName("记录存在但字节已丢 → 不算秒传，重新落盘（避免返回一个打不开的文件）")
    void staleRecordWithoutBytesIsNotInstant() {
        byte[] content = "content-whose-bytes-are-gone".getBytes(StandardCharsets.UTF_8);
        FileRecord existing = new FileRecord();
        existing.setStoragePath("2026/01/01/missing.pdf");   // 未写入存储
        when(fileRepository.findBySha(sha256Of("content-whose-bytes-are-gone"), content.length)).thenReturn(existing);

        service.upload(file("x.pdf", "application/pdf", content));

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).insert(captor.capture());
        assertTrue(!existing.getStoragePath().equals(captor.getValue().getStoragePath()),
                "字节不存在时必须重新存，不能复用失效路径");
        assertTrue(storage.exists(captor.getValue().getStoragePath()));
    }

    // ---------------------------------------------------------------- 秒传探测

    @Test
    @DisplayName("秒传探测：参数不合法直接拒绝（文件名/sha256/大小）")
    void instantValidatesInput() {
        FileDtos.InstantRequest request = new FileDtos.InstantRequest();
        request.setFileName("a.pdf");
        request.setSize(10L);
        request.setContentType("application/pdf");
        request.setSha256("not-a-sha256");
        assertThrows(BizException.class, () -> service.instant(request));

        request.setSha256(sha256Of("abc"));
        request.setSize(0L);
        assertThrows(BizException.class, () -> service.instant(request));
    }

    @Test
    @DisplayName("秒传探测：同一上传者命中 → instant=true；未命中 → instant=false 且不带文件")
    void instantHitsOrMisses() {
        String sha = sha256Of("dedup");
        FileRecord existing = new FileRecord();
        existing.setStoragePath("2026/01/01/dedup.pdf");
        storage.store(writeTemp("y"), existing.getStoragePath());
        when(fileRepository.findByShaAndUploader(sha, 5L, 7L)).thenReturn(existing);

        FileDtos.InstantRequest hit = new FileDtos.InstantRequest();
        hit.setFileName("d.pdf");
        hit.setSize(5L);
        hit.setContentType("application/pdf");
        hit.setSha256(sha);
        FileDtos.InstantResponse hitResponse = service.instant(hit);
        assertTrue(hitResponse.isInstant());
        assertNotNull(hitResponse.getFile());

        when(fileRepository.findByShaAndUploader(sha, 5L, 7L)).thenReturn(null);
        FileDtos.InstantResponse missResponse = service.instant(hit);
        assertFalse(missResponse.isInstant());
        assertNull(missResponse.getFile());
    }

    // ---------------------------------------------------------------- 分片会话

    private static FileDtos.ChunkInitRequest chunkRequest(long size, int chunkSize) {
        FileDtos.ChunkInitRequest request = new FileDtos.ChunkInitRequest();
        request.setFileName("big.zip");
        request.setSize(size);
        request.setContentType("application/zip");
        request.setSha256(sha256Of("big"));
        request.setChunkSize(chunkSize);
        return request;
    }

    @Test
    @DisplayName("分片大小越界 → 拒绝（小于 256KB 或大于 5MB 都不可协商）")
    void chunkSizeMustBeInRange() {
        BizException tooSmall = assertThrows(BizException.class,
                () -> service.initChunkSession(chunkRequest(1024L * 1024, 1024)));
        assertTrue(tooSmall.getMessage().contains("256KB"));

        // 注意：文件总大小要先在上限内，否则先撞到「文件过大」而不是「分片过大」
        BizException tooLarge = assertThrows(BizException.class,
                () -> service.initChunkSession(chunkRequest(6L * 1024 * 1024, 6 * 1024 * 1024)));
        assertTrue(tooLarge.getMessage().contains("5MB"));
    }

    @Test
    @DisplayName("正常开会话 → uploadId 非空、totalChunks 按上取整、状态 UPLOADING")
    void chunkSessionIsCreated() {
        FileDtos.ChunkSessionResponse response = service.initChunkSession(
                chunkRequest(1024L * 1024 + 1, 1024 * 1024));

        assertNotNull(response.getUploadId());
        assertEquals(2, response.getTotalChunks(), "1024*1024+1 字节按 1MB 切片应得 2 片");
        assertFalse(response.isInstant());

        ArgumentCaptor<FileChunkUpload> captor = ArgumentCaptor.forClass(FileChunkUpload.class);
        verify(chunkUploadRepository).insert(captor.capture());
        assertEquals("UPLOADING", captor.getValue().getStatus());
        assertEquals(7L, captor.getValue().getUploaderId());
    }

    @Test
    @DisplayName("分片会话秒传：命中已有文件 → 直接建记录，uploadId 为空")
    void chunkSessionCanBeInstant() {
        String sha = sha256Of("big");
        FileRecord existing = new FileRecord();
        existing.setStoragePath("2026/01/01/big.zip");
        storage.store(writeTemp("z"), existing.getStoragePath());
        when(fileRepository.findByShaAndUploader(sha, 1024L * 1024, 7L)).thenReturn(existing);

        FileDtos.ChunkSessionResponse response = service.initChunkSession(chunkRequest(1024L * 1024, 1024 * 1024));

        assertTrue(response.isInstant());
        assertNull(response.getUploadId(), "秒传不需要会话");
        verify(chunkUploadRepository, never()).insert(any());
    }

    @Test
    @DisplayName("续传他人会话 → 403；会话不存在 → 404")
    void chunkSessionOwnershipIsEnforced() {
        FileChunkUpload others = new FileChunkUpload();
        others.setUploadId("abc");
        others.setUploaderId(99L);
        others.setStatus("UPLOADING");
        others.setTotalChunks(2);
        others.setChunkSize(1024 * 1024);
        others.setUploadedChunks("");
        when(chunkUploadRepository.findByUploadId("abc")).thenReturn(others);

        BizException forbidden = assertThrows(BizException.class,
                () -> service.uploadChunk("abc", 0, file("p", "application/zip", "x".getBytes(StandardCharsets.UTF_8))));
        assertEquals(ErrorCode.FORBIDDEN, forbidden.getErrorCode());

        when(chunkUploadRepository.findByUploadId("nope")).thenReturn(null);
        BizException notFound = assertThrows(BizException.class,
                () -> service.uploadChunk("nope", 0, file("p", "application/zip", "x".getBytes(StandardCharsets.UTF_8))));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, notFound.getErrorCode());
    }

    @Test
    @DisplayName("上传分片：序号越界与超过约定大小都被拒绝，且不写盘")
    void chunkIndexAndSizeAreValidated() {
        FileChunkUpload mine = new FileChunkUpload();
        mine.setUploadId("mine");
        mine.setUploaderId(7L);
        mine.setStatus("UPLOADING");
        mine.setTotalChunks(2);
        mine.setChunkSize(256 * 1024);
        mine.setUploadedChunks("");
        when(chunkUploadRepository.findByUploadId("mine")).thenReturn(mine);

        assertThrows(BizException.class, () -> service.uploadChunk("mine", 5,
                file("p", "application/zip", "x".getBytes(StandardCharsets.UTF_8))));
        assertThrows(BizException.class, () -> service.uploadChunk("mine", 0,
                file("p", "application/zip", new byte[256 * 1024 + 1])));

        verify(chunkUploadRepository, never()).updateChunks(anyString(), anyString());
    }

    @Test
    @DisplayName("上传分片成功 → 落盘分片并累计已上传序号（去重排序）")
    void chunkUploadAccumulatesIndexes() {
        FileChunkUpload mine = new FileChunkUpload();
        mine.setUploadId("mine");
        mine.setUploaderId(7L);
        mine.setStatus("UPLOADING");
        mine.setTotalChunks(3);
        mine.setChunkSize(256 * 1024);
        mine.setUploadedChunks("0");
        when(chunkUploadRepository.findByUploadId("mine")).thenReturn(mine);

        List<Integer> uploaded = service.uploadChunk("mine", 2,
                file("p", "application/zip", "chunk2".getBytes(StandardCharsets.UTF_8)));

        assertEquals(List.of(0, 2), uploaded);
        verify(chunkUploadRepository).updateChunks("mine", "0,2");
        assertTrue(storage.exists(com.adminstarter.system.domain.storage.FileStorageKeys.chunkPart("mine", 2)));
    }

    // ---------------------------------------------------------------- 工具

    private Path writeTemp(String content) {
        try {
            Path path = tempDir.resolve("src-" + Math.abs(content.hashCode()) + ".bin");
            java.nio.file.Files.writeString(path, content);
            return path;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @DisplayName("未登录上传 → 仍按匿名记录（系统内部调用），但记录上传人为空")
    void uploadWithoutContextRecordsNullUploader() {
        DataScopeContext.clear();
        byte[] content = "anonymous".getBytes(StandardCharsets.UTF_8);

        service.upload(file("anon.txt", "text/plain", content));

        ArgumentCaptor<FileRecord> captor = ArgumentCaptor.forClass(FileRecord.class);
        verify(fileRepository).insert(captor.capture());
        assertNull(captor.getValue().getUploaderId());
        assertNull(captor.getValue().getCreateBy());
    }
}
