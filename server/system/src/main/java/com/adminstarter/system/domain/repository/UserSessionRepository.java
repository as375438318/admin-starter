package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.UserSession;
import com.adminstarter.system.domain.query.UserSessionQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话持久化。失效/清理均为显式条件更新，避免覆盖已失效行。
 */
public interface UserSessionRepository {

    void insert(UserSession session);

    UserSession findById(Long id);

    UserSession findBySessionId(String sessionId);

    PageResult<UserSession> page(UserSessionQuery query, LocalDateTime onlineSince);

    /** 当前用户的活跃会话（invalidated_at 为空），按最近活跃倒序。 */
    List<UserSession> findActiveByUserId(Long userId);

    /** 节流后的活跃时间更新；只更新未失效会话。 */
    int touch(String sessionId, LocalDateTime lastActiveAt);

    int invalidateById(Long id, String reason);

    int invalidateByUserId(Long userId, String reason, String excludeSessionId);

    int deleteInvalidatedBefore(LocalDateTime cutoff);
}
