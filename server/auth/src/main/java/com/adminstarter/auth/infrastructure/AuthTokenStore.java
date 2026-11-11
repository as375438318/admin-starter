package com.adminstarter.auth.infrastructure;

import com.adminstarter.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 令牌状态：refresh 用白名单，登出后的 access 用黑名单。
 */
@Component
@RequiredArgsConstructor
public class AuthTokenStore {

    private static final String REFRESH_PREFIX = com.adminstarter.common.cache.CacheNamespaces.AUTH_REFRESH_PREFIX;
    private static final String DENY_PREFIX = com.adminstarter.common.cache.CacheNamespaces.AUTH_DENY_PREFIX;

    private final CacheService cacheService;

    public void saveRefresh(String jti, Long userId, Duration ttl) {
        cacheService.setIfAbsent(REFRESH_PREFIX + jti, String.valueOf(userId), ttl);
    }

    public boolean isRefreshActive(String jti) {
        return cacheService.exists(REFRESH_PREFIX + jti);
    }

    public void revokeRefresh(String jti) {
        cacheService.delete(REFRESH_PREFIX + jti);
    }

    public void denyAccess(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        cacheService.put(DENY_PREFIX + jti, "1", ttl);
    }

    public boolean isAccessDenied(String jti) {
        return cacheService.exists(DENY_PREFIX + jti);
    }
}
