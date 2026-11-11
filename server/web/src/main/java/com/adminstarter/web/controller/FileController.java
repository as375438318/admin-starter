package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.FileAppService;
import com.adminstarter.system.application.dto.FileDtos;
import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.web.log.OperLogRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileAppService fileAppService;

    @PostMapping
    @PreAuthorize("hasAuthority('system:file:upload')")
    @OperLogRecord(module = "文件管理", action = "上传文件")
    public ApiResponse<FileDtos.Response> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(fileAppService.upload(file));
    }

    @PostMapping("/instant")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<FileDtos.InstantResponse> instant(@RequestBody FileDtos.InstantRequest request) {
        return ApiResponse.ok(fileAppService.instant(request));
    }

    @PostMapping("/chunk-sessions")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<FileDtos.ChunkSessionResponse> initChunkSession(
            @RequestBody FileDtos.ChunkInitRequest request) {
        return ApiResponse.ok(fileAppService.initChunkSession(request));
    }

    @GetMapping("/chunk-sessions/{uploadId}")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<FileDtos.ChunkSessionResponse> chunkStatus(@PathVariable String uploadId) {
        return ApiResponse.ok(fileAppService.chunkStatus(uploadId));
    }

    @PutMapping("/chunk-sessions/{uploadId}/chunks/{index}")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<List<Integer>> uploadChunk(@PathVariable String uploadId,
                                                 @PathVariable int index,
                                                 @RequestPart("chunk") MultipartFile chunk) {
        return ApiResponse.ok(fileAppService.uploadChunk(uploadId, index, chunk));
    }

    @PostMapping("/chunk-sessions/{uploadId}/complete")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<FileDtos.Response> completeChunkSession(@PathVariable String uploadId) {
        return ApiResponse.ok(fileAppService.completeChunkSession(uploadId));
    }

    @DeleteMapping("/chunk-sessions/{uploadId}")
    @PreAuthorize("hasAuthority('system:file:upload')")
    public ApiResponse<Void> abortChunkSession(@PathVariable String uploadId) {
        fileAppService.abortChunkSession(uploadId);
        return ApiResponse.ok(null);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:file:list')")
    public ApiResponse<PageResponse<FileDtos.Response>> page(PageQuery pageQuery,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(fileAppService.page(pageQuery, keyword));
    }

    @GetMapping("/recycle")
    @PreAuthorize("hasAuthority('system:file:list')")
    public ApiResponse<PageResponse<FileDtos.Response>> recyclePage(PageQuery pageQuery,
                                                                   @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(fileAppService.recyclePage(pageQuery, keyword));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:file:delete')")
    @OperLogRecord(module = "文件管理", action = "删除文件")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fileAppService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('system:file:restore')")
    @OperLogRecord(module = "文件管理", action = "恢复文件")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        fileAppService.restore(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/recycle/{id}")
    @PreAuthorize("hasAuthority('system:file:purge')")
    @OperLogRecord(module = "文件管理", action = "彻底删除文件")
    public ApiResponse<Void> purge(@PathVariable Long id) {
        fileAppService.purge(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('system:file:download')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileRecord record = fileAppService.requireVisible(id);
        Resource resource = fileAppService.loadResource(record);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(record.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .contentType(fileAppService.downloadMediaType(record))
                .contentLength(record.getSize() == null ? 0 : record.getSize())
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('system:file:download')")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        FileRecord record = fileAppService.requirePreviewable(id);
        Resource resource = fileAppService.loadResource(record);
        MediaType mediaType = fileAppService.previewMediaType(record);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(record.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'")
                .contentType(mediaType)
                .contentLength(record.getSize() == null ? 0 : record.getSize())
                .body(resource);
    }
}
