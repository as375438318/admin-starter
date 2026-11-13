package com.adminstarter.system.application.dto;

import lombok.Data;

import java.time.Instant;

/** 变更审计的只读视图模型（写入一律走 ChangeAuditRecorder，不开放创建接口）。 */
public final class ChangeAuditDtos {

    private ChangeAuditDtos() {
    }

    @Data
    public static class Response {
        private Long id;
        private String targetType;
        private String targetKey;
        private String targetName;
        private String action;
        /** 变更前快照（JSON 字符串，已按敏感标记打码） */
        private String beforeValue;
        private String afterValue;
        private Boolean isSensitive;
        private Long operatorId;
        private String operatorName;
        private String ip;
        private String traceId;
        private Instant createdAt;
    }
}