package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file_chunk_upload")
public class FileChunkUploadDO {

    @TableId(type = IdType.AUTO)
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
