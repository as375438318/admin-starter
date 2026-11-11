package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录会话记录。运行态数据，不做软删除（清理为物理删除，见 docs/design/session-management.md §8）。
 * <p>失效原因常量：主动退出 / 管理员踢下线 / 密码变更 / 账号停用 / 账号删除。</p>
 */
@Data
public class UserSession {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_KICKED_BY_ADMIN = "KICKED_BY_ADMIN";
    public static final String REASON_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String REASON_USER_DELETED = "USER_DELETED";

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
    /** 用户昵称（查询时关联 sys_user 得到，不落本表）。 */
    private String nickname;

    public boolean isInvalidated() {
        return invalidatedAt != null;
    }
}
