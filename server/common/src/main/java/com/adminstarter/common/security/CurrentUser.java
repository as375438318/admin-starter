package com.adminstarter.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 当前登录用户快照，由认证过滤器从 JWT 解析后写入 SecurityContext。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private Long userId;
    private String username;
    private Long deptId;
    /** 当前请求所在会话标识（令牌 sid）；用于用户端“当前设备”标记与密码变更时排除自身。 */
    private String sessionId;
    private Set<String> roles;
    private Set<String> permissions;

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
