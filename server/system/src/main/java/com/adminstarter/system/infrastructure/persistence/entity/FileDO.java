package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_file")
public class FileDO {

    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer deleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
}
