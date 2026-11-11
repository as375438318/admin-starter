package com.adminstarter.system.application.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class FileDtos {

    private FileDtos() {
    }

    @Data
    public static class Response {
        private Long id;
        private String originalName;
        private String contentType;
        private Long size;
        private Long uploaderId;
        private Instant createdAt;
        private String downloadUrl;
        private String previewUrl;
        private Boolean instant;
        private Instant deletedAt;
        private Long deletedBy;
    }

    @Data
    public static class InstantRequest {
        private String fileName;
        private Long size;
        private String contentType;
        private String sha256;
    }

    @Data
    public static class InstantResponse {
        private boolean instant;
        private Response file;
    }

    @Data
    public static class ChunkInitRequest {
        private String fileName;
        private Long size;
        private String contentType;
        private String sha256;
        private Integer chunkSize;
    }

    @Data
    public static class ChunkSessionResponse {
        private String uploadId;
        private int chunkSize;
        private int totalChunks;
        private List<Integer> uploadedChunks;
        private boolean instant;
        private Response file;
    }
}
