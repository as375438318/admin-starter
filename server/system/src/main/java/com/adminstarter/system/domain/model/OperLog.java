package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志是只追加的审计记录，不参与逻辑删除。
 */
@Data
public class OperLog {

    private Long id;
    private String traceId;
    private Long userId;
    private String username;
    private Long deptId;
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
    private Long createBy;
    private LocalDateTime createdAt;
}
