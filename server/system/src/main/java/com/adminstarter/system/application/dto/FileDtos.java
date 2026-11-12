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
        /**
         * 仅在命中秒传时有值。<b>为 null 时不序列化该字段</b>：OAS 3.0 的 `nullable` 无法与 `allOf` 的 $ref
         * 组合表达（契约校验器仍会按 object 校验 null），因此用"字段缺省"表达"没有"（CR-20261112-034）。
         */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
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
        /**
         * 仅在命中秒传时有值。<b>为 null 时不序列化该字段</b>：OAS 3.0 的 `nullable` 无法与 `allOf` 的 $ref
         * 组合表达（契约校验器仍会按 object 校验 null），因此用"字段缺省"表达"没有"（CR-20261112-034）。
         */
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        private Response file;
    }
}
