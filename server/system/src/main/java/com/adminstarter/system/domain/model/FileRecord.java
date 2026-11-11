package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileRecord {

    private Long id;
    private String originalName;
    private String storedName;
    private String storagePath;
    private String contentType;
    private Long size;
    private String sha256;
    private Long uploaderId;
    private Long deptId;
    private Long createBy;
    private LocalDateTime createdAt;
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
}
