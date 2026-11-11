package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 应读人快照与已读记录；readAt 为空表示未读。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeRecipient extends BaseModel {

    private Long noticeId;
    private Long userId;
    private LocalDateTime readAt;
}
