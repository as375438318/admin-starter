package com.adminstarter.system.domain.repository;

import com.adminstarter.system.domain.model.FileChunkUpload;

import java.time.LocalDateTime;
import java.util.List;

public interface FileChunkUploadRepository {

    void insert(FileChunkUpload session);

    FileChunkUpload findByUploadId(String uploadId);

    void updateChunks(String uploadId, String chunks);

    void updateStatus(String uploadId, String status);

    List<FileChunkUpload> findStaleUploading(LocalDateTime cutoff);
}
