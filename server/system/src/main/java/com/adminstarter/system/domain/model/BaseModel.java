package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领域模型公共字段；不含任何框架注解，符合 BE-ARCH-004。
 */
@Data
public abstract class BaseModel {

    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
