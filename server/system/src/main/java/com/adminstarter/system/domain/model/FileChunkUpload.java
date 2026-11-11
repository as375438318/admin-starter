package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileChunkUpload {

    private Long id;
    private String uploadId;
    private Long uploaderId;
    private Long deptId;
    private String originalName;
    private String contentType;
    private Long size;
    private String sha256;
    private Integer chunkSize;
    private Integer totalChunks;
    private String uploadedChunks;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
