package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.error.PayloadTooLargeException;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.FileDtos;
import com.adminstarter.system.domain.model.FileChunkUpload;
import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.system.domain.repository.FileChunkUploadRepository;
import com.adminstarter.system.domain.repository.FileRepository;
import com.adminstarter.system.domain.storage.FileStorage;
import com.adminstarter.system.domain.storage.FileStorageKeys;
import com.adminstarter.system.infrastructure.persistence.DataPermissionContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAppService {

    /** 可在线预览的扩展名；其余类型只允许下载（避免 html/svg 等 inline XSS）。 */
    private static final Set<String> PREVIEW_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf");
    private static final int CHUNK_MIN_SIZE = 256 * 1024;
    private static final int CHUNK_MAX_SIZE = 5 * 1024 * 1024;
    private static final int MAX_TOTAL_CHUNKS = 1000;
    private static final String SHA256_PATTERN = "[a-f0-9]{64}";

    private final FileRepository fileRepository;
    private final FileChunkUploadRepository chunkUploadRepository;
    private final PublicConfigAppService publicConfigAppService;
    private final FileStorage fileStorage;

    @Value("${app.file.upload-dir:./data/uploads}")
    private String uploadDir;

    @Value("${app.file.allowed-extensions:jpg,jpeg,png,gif,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip}")
    private String allowedExtensions;

    private Path tempRoot;
    private Set<String> extensions;

    @PostConstruct
    public void init() {
        tempRoot = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(FileStorageKeys.TEMP_ROOT);
        extensions = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim).map(s -> s.toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        try {
            Files.createDirectories(tempRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传临时目录: " + tempRoot, e);
        }
    }

    // ---------------------------------------------------------------- 上传

    @Transactional
    public FileDtos.Response upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "文件不能为空");
        }
        long maxSize = publicConfigAppService.uploadMaxSize();
        if (file.getSize() > maxSize) {
            throw new PayloadTooLargeException(maxSize,
                    "文件大小不能超过 " + PublicConfigAppService.formatMb(maxSize) + "MB");
        }
        String originalName = sanitizeName(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (!extensions.contains(ext)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "不支持的文件类型: " + ext);
        }
        CurrentUser current = DataScopeContext.get();
        Path temp = createTempFile();
        String sha256;
        try {
            sha256 = hashToTemp(file, temp);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件写入失败");
        }
        try {
            FileRecord existing = fileRepository.findBySha(sha256, file.getSize());
            boolean instant = existing != null && fileStorage.exists(existing.getStoragePath());
            String storagePath;
            if (instant) {
                storagePath = existing.getStoragePath();
                deleteQuietly(temp);
            } else {
                storagePath = FileStorageKeys.forSha256(sha256);
                fileStorage.store(temp, storagePath);
            }
            FileRecord record = insertRecord(originalName, file.getContentType(), file.getSize(), sha256,
                    storagePath, current);
            return toResponse(record, instant);
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    /** 秒传探测：只匹配同一上传者，命中则直接建记录，不重复存字节。 */
    @Transactional
    public FileDtos.InstantResponse instant(FileDtos.InstantRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "参数不能为空");
        }
        String originalName = sanitizeName(request.getFileName());
        long size = requireSize(request.getSize());
        requireSha256(request.getSha256());
        requireContentType(request.getContentType());
        String ext = extensionOf(originalName);
        if (!extensions.contains(ext)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "不支持的文件类型: " + ext);
        }
        CurrentUser current = requireCurrentUser();
        FileDtos.InstantResponse response = new FileDtos.InstantResponse();
        FileRecord existing = fileRepository.findByShaAndUploader(request.getSha256(), size, current.getUserId());
        if (existing != null && fileStorage.exists(existing.getStoragePath())) {
            FileRecord record = insertRecord(originalName, request.getContentType(), size, request.getSha256(),
                    existing.getStoragePath(), current);
            response.setInstant(true);
            response.setFile(toResponse(record, true));
        }
        return response;
    }

    // -------------------------------------------------------------- 分片上传

    @Transactional
    public FileDtos.ChunkSessionResponse initChunkSession(FileDtos.ChunkInitRequest request) {
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "参数不能为空");
        }
        String originalName = sanitizeName(request.getFileName());
        long size = requireSize(request.getSize());
        requireSha256(request.getSha256());
        requireContentType(request.getContentType());
        long maxSize = publicConfigAppService.uploadMaxSize();
        if (size > maxSize) {
            throw new PayloadTooLargeException(maxSize,
                    "文件大小不能超过 " + PublicConfigAppService.formatMb(maxSize) + "MB");
        }
        String ext = extensionOf(originalName);
        if (!extensions.contains(ext)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "不支持的文件类型: " + ext);
        }
        int chunkSize = request.getChunkSize() == null ? 0 : request.getChunkSize();
        if (chunkSize < CHUNK_MIN_SIZE || chunkSize > CHUNK_MAX_SIZE) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "分片大小必须在 256KB–5MB 之间");
        }
        int totalChunks = (int) ((size + chunkSize - 1) / chunkSize);
        if (totalChunks < 1 || totalChunks > MAX_TOTAL_CHUNKS) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "分片数量不合法");
        }
        CurrentUser current = requireCurrentUser();
        FileRecord existing = fileRepository.findByShaAndUploader(request.getSha256(), size, current.getUserId());
        if (existing != null && fileStorage.exists(existing.getStoragePath())) {
            FileRecord record = insertRecord(originalName, request.getContentType(), size, request.getSha256(),
                    existing.getStoragePath(), current);
            FileDtos.ChunkSessionResponse response = new FileDtos.ChunkSessionResponse();
            response.setUploadId(null);
            response.setChunkSize(chunkSize);
            response.setTotalChunks(totalChunks);
            response.setUploadedChunks(List.of());
            response.setInstant(true);
            response.setFile(toResponse(record, true));
            return response;
        }
        FileChunkUpload session = new FileChunkUpload();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setUploaderId(current.getUserId());
        session.setDeptId(current.getDeptId());
        session.setOriginalName(originalName);
        session.setContentType(request.getContentType());
        session.setSize(size);
        session.setSha256(request.getSha256());
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks("");
        session.setStatus("UPLOADING");
        session.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        session.setUpdatedAt(session.getCreatedAt());
        chunkUploadRepository.insert(session);
        return toChunkResponse(session, false, null);
    }

    @Transactional
    public List<Integer> uploadChunk(String uploadId, int index, MultipartFile chunk) {
        FileChunkUpload session = requireChunkSession(uploadId);
        if (!"UPLOADING".equals(session.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前会话不可继续上传");
        }
        if (index < 0 || index >= session.getTotalChunks()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "分片序号超出范围");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "分片不能为空");
        }
        if (chunk.getSize() > session.getChunkSize()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "分片大小超过约定值");
        }
        Path temp = createTempFile();
        try (InputStream in = chunk.getInputStream()) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "分片写入失败");
        }
        fileStorage.store(temp, FileStorageKeys.chunkPart(uploadId, index));
        TreeSet<Integer> uploaded = parseChunks(session.getUploadedChunks());
        uploaded.add(index);
        chunkUploadRepository.updateChunks(uploadId, joinChunks(uploaded));
        return new ArrayList<>(uploaded);
    }

    public FileDtos.ChunkSessionResponse chunkStatus(String uploadId) {
        FileChunkUpload session = requireChunkSession(uploadId);
        return toChunkResponse(session, false, null);
    }

    @Transactional
    public FileDtos.Response completeChunkSession(String uploadId) {
        FileChunkUpload session = requireChunkSession(uploadId);
        if (!"UPLOADING".equals(session.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "当前会话不可完成");
        }
        TreeSet<Integer> uploaded = parseChunks(session.getUploadedChunks());
        for (int i = 0; i < session.getTotalChunks(); i++) {
            if (!uploaded.contains(i)) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "分片不完整，缺少第 " + i + " 片");
            }
        }
        CurrentUser current = requireCurrentUser();
        Path merged = createTempFile();
        String actualSha;
        long actualSize = 0;
        try (OutputStream out = Files.newOutputStream(merged);
             DigestOutputStream digest = new DigestOutputStream(out, MessageDigest.getInstance("SHA-256"))) {
            for (int i = 0; i < session.getTotalChunks(); i++) {
                Resource part = fileStorage.loadAsResource(FileStorageKeys.chunkPart(uploadId, i));
                try (InputStream in = part.getInputStream()) {
                    actualSize += in.transferTo(digest);
                }
            }
            digest.flush();
            actualSha = HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteQuietly(merged);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "分片合并失败");
        }
        if (actualSize != session.getSize()) {
            deleteQuietly(merged);
            throw new BizException(ErrorCode.VALIDATION_FAILED, "合并后文件大小与声明不一致");
        }
        if (!actualSha.equalsIgnoreCase(session.getSha256())) {
            deleteQuietly(merged);
            throw new BizException(ErrorCode.VALIDATION_FAILED, "合并后文件校验失败，请重新上传");
        }
        try {
            FileRecord existing = fileRepository.findBySha(actualSha, actualSize);
            boolean instant = existing != null && fileStorage.exists(existing.getStoragePath());
            String storagePath;
            if (instant) {
                storagePath = existing.getStoragePath();
                deleteQuietly(merged);
            } else {
                storagePath = FileStorageKeys.forSha256(actualSha);
                fileStorage.store(merged, storagePath);
            }
            FileRecord record = insertRecord(session.getOriginalName(), session.getContentType(), session.getSize(),
                    actualSha, storagePath, current);
            fileStorage.deleteRecursively(FileStorageKeys.chunkRoot(uploadId));
            chunkUploadRepository.updateStatus(uploadId, "MERGED");
            return toResponse(record, instant);
        } catch (RuntimeException e) {
            deleteQuietly(merged);
            throw e;
        }
    }

    @Transactional
    public void abortChunkSession(String uploadId) {
        FileChunkUpload session = requireChunkSession(uploadId);
        fileStorage.deleteRecursively(FileStorageKeys.chunkRoot(uploadId));
        if ("UPLOADING".equals(session.getStatus())) {
            chunkUploadRepository.updateStatus(uploadId, "ABORTED");
        }
    }

    // ---------------------------------------------------------------- 查询

    public PageResponse<FileDtos.Response> page(PageQuery pageQuery, String keyword) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        PageResult<FileRecord> result = DataPermissionContext.controlled(
                () -> fileRepository.page(keyword, page, pageSize));
        return PageResponse.of(page, pageSize, result.getList().stream().map(this::toResponse).toList(),
                result.getTotal());
    }

    public PageResponse<FileDtos.Response> recyclePage(PageQuery pageQuery, String keyword) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        PageResult<FileRecord> result = DataPermissionContext.controlled(
                () -> fileRepository.pageDeleted(keyword, page, pageSize));
        return PageResponse.of(page, pageSize, result.getList().stream().map(this::toResponse).toList(),
                result.getTotal());
    }

    // ---------------------------------------------------------------- 删除/回收站

    /** 删除进回收站：记录不存在或不在可见范围时也返回成功，保证重复删除幂等。 */
    @Transactional
    public void delete(Long id) {
        FileRecord record = DataPermissionContext.controlled(() -> fileRepository.findById(id));
        if (record == null) {
            return;
        }
        CurrentUser current = DataScopeContext.get();
        fileRepository.moveToRecycle(id, current == null ? null : current.getUserId());
    }

    @Transactional
    public void restore(Long id) {
        FileRecord record = DataPermissionContext.controlled(() -> fileRepository.findDeletedById(id));
        if (record == null) {
            return;
        }
        fileRepository.restore(id);
    }

    /** 彻底删除：先删行，再按 storage_path 计数决定是否删物理字节（共享去重字节需保留）。 */
    @Transactional
    public void purge(Long id) {
        FileRecord record = DataPermissionContext.controlled(() -> fileRepository.findDeletedById(id));
        if (record == null) {
            return;
        }
        fileRepository.purge(id);
        if (fileRepository.countByStoragePath(record.getStoragePath()) == 0) {
            fileStorage.delete(record.getStoragePath());
        }
    }

    // ---------------------------------------------------------------- 下载/预览

    /**
     * 按 id 取"当前用户可见"的文件记录。
     * <p>越权与不存在**返回同一个错误**（`404` + 同一文案），不暴露资源是否存在（`CT-HTTP-008`、`CT-ERR-009`）。
     * 历史实现曾对"存在但不可见"返回 `403`，与契约冲突；2026-11-11 按契约统一为 `404`。</p>
     */
    public FileRecord requireVisible(Long id) {
        FileRecord raw = fileRepository.findById(id);
        if (raw == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在或无权访问");
        }
        FileRecord visible = DataPermissionContext.controlled(() -> fileRepository.findById(id));
        if (visible == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在或无权访问");
        }
        return visible;
    }

    public FileRecord requirePreviewable(Long id) {
        FileRecord record = requireVisible(id);
        if (!PREVIEW_EXTENSIONS.contains(extensionOf(record.getOriginalName())) || !isPreviewContentType(record)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "该文件类型不支持在线预览，请下载查看");
        }
        return record;
    }

    public Resource loadResource(FileRecord record) {
        return fileStorage.loadAsResource(record.getStoragePath());
    }

    public MediaType downloadMediaType(FileRecord record) {
        if (record.getContentType() == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(record.getContentType());
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    public MediaType previewMediaType(FileRecord record) {
        return switch (extensionOf(record.getOriginalName())) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.IMAGE_JPEG;
        };
    }

    // ---------------------------------------------------------------- 内部

    private FileRecord insertRecord(String originalName, String contentType, long size, String sha256,
                                    String storagePath, CurrentUser current) {
        String storedName = storagePath.substring(storagePath.lastIndexOf('/') + 1);
        FileRecord record = new FileRecord();
        record.setOriginalName(originalName);
        record.setStoredName(storedName);
        record.setStoragePath(storagePath);
        record.setContentType(contentType);
        record.setSize(size);
        record.setSha256(sha256);
        record.setUploaderId(current == null ? null : current.getUserId());
        record.setDeptId(current == null ? null : current.getDeptId());
        record.setCreateBy(current == null ? null : current.getUserId());
        fileRepository.insert(record);
        return record;
    }

    private FileChunkUpload requireChunkSession(String uploadId) {
        FileChunkUpload session = chunkUploadRepository.findByUploadId(uploadId);
        if (session == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "分片会话不存在");
        }
        CurrentUser current = requireCurrentUser();
        if (session.getUploaderId() == null || !session.getUploaderId().equals(current.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该分片会话");
        }
        return session;
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser current = DataScopeContext.get();
        if (current == null || current.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return current;
    }

    private FileDtos.ChunkSessionResponse toChunkResponse(FileChunkUpload session, boolean instant,
                                                           FileDtos.Response file) {
        FileDtos.ChunkSessionResponse response = new FileDtos.ChunkSessionResponse();
        response.setUploadId(session.getUploadId());
        response.setChunkSize(session.getChunkSize());
        response.setTotalChunks(session.getTotalChunks());
        response.setUploadedChunks(new ArrayList<>(parseChunks(session.getUploadedChunks())));
        response.setInstant(instant);
        response.setFile(file);
        return response;
    }

    private FileDtos.Response toResponse(FileRecord record) {
        return toResponse(record, null);
    }

    private FileDtos.Response toResponse(FileRecord record, Boolean instant) {
        FileDtos.Response response = new FileDtos.Response();
        response.setId(record.getId());
        response.setOriginalName(record.getOriginalName());
        response.setContentType(record.getContentType());
        response.setSize(record.getSize());
        response.setUploaderId(record.getUploaderId());
        response.setCreatedAt(TimeSupport.toInstant(record.getCreatedAt()));
        response.setDownloadUrl("/api/v1/files/" + record.getId() + "/download");
        if (PREVIEW_EXTENSIONS.contains(extensionOf(record.getOriginalName())) && isPreviewContentType(record)) {
            response.setPreviewUrl("/api/v1/files/" + record.getId() + "/preview");
        }
        response.setInstant(instant);
        response.setDeletedAt(TimeSupport.toInstant(record.getDeletedAt()));
        response.setDeletedBy(record.getDeletedBy());
        return response;
    }

    private boolean isPreviewContentType(FileRecord record) {
        String ext = extensionOf(record.getOriginalName());
        String type = record.getContentType() == null ? "" : record.getContentType().toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp" -> true;
            case "pdf" -> type.isEmpty() || type.contains("pdf") || type.equals("application/octet-stream");
            default -> false;
        };
    }

    private String hashToTemp(MultipartFile file, Path target) throws IOException {
        try (InputStream in = file.getInputStream();
             DigestInputStream digest = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
             OutputStream out = Files.newOutputStream(target)) {
            digest.transferTo(out);
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private Path createTempFile() {
        try {
            Files.createDirectories(tempRoot);
            return Files.createTempFile(tempRoot, "up-", ".tmp");
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "无法创建临时文件");
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("临时文件删除失败: {}", path, e);
        }
    }

    private long requireSize(Long size) {
        if (size == null || size <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "文件大小必须大于 0");
        }
        return size;
    }

    private void requireSha256(String sha256) {
        if (sha256 == null || !sha256.matches(SHA256_PATTERN)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "sha256 必须是 64 位小写十六进制");
        }
    }

    private void requireContentType(String contentType) {
        if (contentType != null && contentType.length() > 128) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "contentType 过长");
        }
    }

    private String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        // 文件名中出现路径分隔符、盘符或 .. 一律拒绝，不做“静默改名”，避免路径穿越被掩盖。
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(":")) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "文件名不合法");
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty() || name.length() > 255) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "文件名不合法");
        }
        return name;
    }

    private String extensionOf(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private TreeSet<Integer> parseChunks(String value) {
        TreeSet<Integer> chunks = new TreeSet<>();
        if (value == null || value.isBlank()) {
            return chunks;
        }
        for (String part : value.split(",")) {
            try {
                chunks.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // 历史脏数据跳过，不影响续传
            }
        }
        return chunks;
    }

    private String joinChunks(TreeSet<Integer> chunks) {
        return chunks.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
