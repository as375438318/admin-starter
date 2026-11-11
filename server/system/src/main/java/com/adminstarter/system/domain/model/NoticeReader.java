package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已读统计中的接收人条目。
 */
@Data
public class NoticeReader {

    private Long userId;
    private String username;
    private String nickname;
    private String deptName;
    private LocalDateTime readAt;
}
