package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知领域模型（站内信渠道）。已撤回 = revokedAt 非空。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Notice extends BaseModel {

    private String title;
    private String content;
    private String level;
    private String type;
    private Boolean revocable;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private Long deptId;
    private Long createBy;
}
