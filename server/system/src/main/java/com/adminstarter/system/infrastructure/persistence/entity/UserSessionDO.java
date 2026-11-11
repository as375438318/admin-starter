package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话表持久化对象。**不继承 BaseDO**：该表没有 deleted 列，也不能走逻辑删除。
 */
@Data
@TableName("sys_user_session")
public class UserSessionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long userId;
    private String username;
    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;
    private String ip;
    private String userAgent;
    private String deviceType;
    private String browser;
    private String os;
    private LocalDateTime invalidatedAt;
    private String invalidateReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 管理端列表关联 sys_user 得到的昵称，非本表字段。 */
    @TableField(exist = false)
    private String nickname;
}
