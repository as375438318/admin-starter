package com.adminstarter.system.infrastructure.persistence.entity;

import com.adminstarter.system.infrastructure.persistence.DataPermissionExempt;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice_scope")
@DataPermissionExempt(reason = "接收范围仅随通知 id 读取，不单独承载可见范围")
public class NoticeScopeDO extends BaseDO {

    private Long noticeId;
    private String scopeType;
    private Long targetId;
}
