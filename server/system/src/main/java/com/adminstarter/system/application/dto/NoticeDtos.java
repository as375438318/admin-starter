package com.adminstarter.system.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class NoticeDtos {

    private NoticeDtos() {
    }

    @Data
    public static class PublishRequest {
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 字")
        private String title;

        @NotBlank(message = "内容不能为空")
        private String content;

        /** NORMAL / IMPORTANT / URGENT，空默认 NORMAL */
        private String level;

        /** SYSTEM / BUSINESS / SECURITY / TASK，空默认 SYSTEM */
        private String type;

        private Boolean revocable;

        @NotEmpty(message = "接收范围不能为空")
        @Valid
        private List<ScopeRequest> scopes;
    }

    @Data
    public static class ScopeRequest {
        /** ALL / ROLE / DEPT / USER */
        @NotBlank(message = "接收范围类型不能为空")
        private String scopeType;

        private Long targetId;
    }

    @Data
    public static class ScopeResponse {
        private String scopeType;
        private Long targetId;
        private String targetName;
    }

    @Data
    public static class ReadStats {
        private long totalRecipients;
        private long readCount;
        private long unreadCount;
    }

    @Data
    public static class Response {
        private Long id;
        private String title;
        private String content;
        private String level;
        private String type;
        private Boolean revocable;
        private Boolean revoked;
        private Instant publishedAt;
        private Long publisherId;
        private String publisherName;
        private Long deptId;
        private String deptName;
        private Instant revokedAt;
        private Long revokedBy;
        private String revokedByName;
        private List<ScopeResponse> scopes;
        private ReadStats stats;
        private Instant createdAt;
    }

    @Data
    public static class ReaderResponse {
        private Long userId;
        private String username;
        private String nickname;
        private String deptName;
        private Boolean read;
        private Instant readAt;
    }

    @Data
    public static class InboxResponse {
        private Long id;
        private String title;
        private String level;
        private String type;
        private Instant publishedAt;
        private String publisherName;
        private Boolean read;
        private Instant readAt;
    }

    @Data
    public static class InboxDetailResponse {
        private Long id;
        private String title;
        private String content;
        private String level;
        private String type;
        private Instant publishedAt;
        private String publisherName;
        private Boolean read;
        private Instant readAt;
    }

    @Data
    public static class UnreadCountResponse {
        private long count;
    }

    @Data
    public static class ReadAllResponse {
        private int marked;
    }
}
