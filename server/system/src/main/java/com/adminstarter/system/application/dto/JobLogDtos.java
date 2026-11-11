package com.adminstarter.system.application.dto;

import lombok.Data;

import java.time.Instant;

public final class JobLogDtos {

    private JobLogDtos() {
    }

    @Data
    public static class Response {
        private Long id;
        private Long jobId;
        private String jobName;
        private String jobKey;
        private String triggerType;
        private Instant startedAt;
        private Instant finishedAt;
        private Long elapsedMs;
        private String result;
        private Integer attemptCount;
        private String errorSummary;
        private Long affectedRows;
    }

    @Data
    public static class DetailResponse {
        private Long id;
        private Long jobId;
        private String jobName;
        private String jobKey;
        private String triggerType;
        private Instant startedAt;
        private Instant finishedAt;
        private Long elapsedMs;
        private String result;
        private Integer attemptCount;
        private String errorSummary;
        private String errorStack;
        private Long affectedRows;
    }
}
