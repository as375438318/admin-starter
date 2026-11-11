package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端收件箱条目（通知 + 本人已读状态）。
 */
@Data
public class NoticeInboxItem {

    private Long noticeId;
    private String title;
    private String content;
    private String level;
    private String type;
    private LocalDateTime publishedAt;
    private Long publisherId;
    private String publisherName;
    private LocalDateTime readAt;
}
