package com.adminstarter.system.infrastructure.persistence.entity;

import com.adminstarter.system.infrastructure.persistence.DataPermissionExempt;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice_recipient")
@DataPermissionExempt(reason = "个人收件箱按 user_id 精确过滤，不参与组织范围")
public class NoticeRecipientDO extends BaseDO {

    private Long noticeId;
    private Long userId;
    private LocalDateTime readAt;
}
