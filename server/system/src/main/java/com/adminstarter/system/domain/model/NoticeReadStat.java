package com.adminstarter.system.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条通知的应读/已读汇总。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeReadStat {

    private Long noticeId;
    private long totalRecipients;
    private long readCount;
}
