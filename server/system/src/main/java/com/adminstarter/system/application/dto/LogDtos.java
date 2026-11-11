package com.adminstarter.system.application.dto;

import lombok.Data;

import java.time.Instant;

public final class LogDtos {

    private LogDtos() {
    }

    @Data
    public static class OperLogResponse {
        private Long id;
        private String traceId;
        private Long userId;
        private String username;
        private Long deptId;
        private String deptName;
        private String module;
        private String action;
        private String method;
        private String requestUri;
        private String requestMethod;
        private String ip;
        private String params;
        private Integer resultCode;
        private String resultMessage;
        private Long elapsedMs;
        private Instant createdAt;
    }

    @Data
    public static class LoginLogResponse {
        private Long id;
        private Long userId;
        private String username;
        private String ip;
        private String userAgent;
        private Boolean isSuccess;
        private String message;
        private Instant createdAt;
    }
}
