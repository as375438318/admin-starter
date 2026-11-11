package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.SessionDtos;
import com.adminstarter.system.domain.model.UserSession;
import com.adminstarter.system.domain.query.UserSessionQuery;
import com.adminstarter.system.domain.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 会话服务：认证侧（登录/刷新/退出）与会话管理端共用。
 * <p>职责边界：本类只做设备级会话事实；账号级失效仍由 sys_user.token_version 负责。
 * 校验顺序与每请求成本见 docs/design/session-management.md §4–§5。</p>
 */
@Service
@RequiredArgsConstructor
public class UserSessionAppService {

    /** 在线窗口：最近 5 分钟内有请求且未失效。 */
    public static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);
    /** 活跃时间节流窗口：每会话最多 60 秒写一次库。 */
    public static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);
    /** 失效记录保留期：清理只删除失效超过 30 天的行。 */
    public static final Duration CLEANUP_RETENTION = Duration.ofDays(30);

    private static final String TOUCH_PREFIX = com.adminstarter.common.cache.CacheNamespaces.SESSION_TOUCH_PREFIX;

    private final UserSessionRepository sessionRepository;
    private final CacheService cacheService;

    // ------------------------------------------------------------------ 认证侧端口

    /** 登录成功时创建会话记录，返回会话标识（写入 access/refresh 令牌的 sid）。 */
    public UserSession openSession(Long userId, String username, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setUsername(username);
        session.setLoginAt(now);
        session.setLastActiveAt(now);
        session.setIp(truncate(ip, 64));
        session.setUserAgent(truncate(userAgent, 255));
        session.setDeviceType(UserAgentParser.deviceType(userAgent));
        session.setBrowser(truncate(UserAgentParser.browser(userAgent), 64));
        session.setOs(truncate(UserAgentParser.os(userAgent), 64));
        sessionRepository.insert(session);
        return session;
    }

    public UserSession findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionRepository.findBySessionId(sessionId);
    }

    /**
     * 活跃时间节流：Redis SETNX 60 秒，只有首次写入才更新库。
     * <p>缓存不可用时不阻断请求，只跳过本次更新。</p>
     */
    public void touch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            if (cacheService.setIfAbsent(TOUCH_PREFIX + sessionId, "1", TOUCH_INTERVAL)) {
                sessionRepository.touch(sessionId, LocalDateTime.now(ZoneOffset.UTC));
            }
        } catch (RuntimeException ignored) {
            // 节流键不可用：跳过写库，不影响鉴权
        }
    }

    /** 退出登录：只失效当前会话，原因记为主动退出。 */
    public boolean invalidateBySessionId(String sessionId, String reason) {
        UserSession session = findBySessionId(sessionId);
        if (session == null) {
            return false;
        }
        return sessionRepository.invalidateById(session.getId(), reason) > 0;
    }

    /** 账号级事件联动：失效该用户全部活跃会话。 */
    public int invalidateUser(Long userId, String reason) {
        return sessionRepository.invalidateByUserId(userId, reason, null);
    }

    /** 账号级事件联动，保留当前会话（本人改密时当前设备继续可用）。 */
    public int invalidateUserExcept(Long userId, String reason, String excludeSessionId) {
        return sessionRepository.invalidateByUserId(userId, reason, excludeSessionId);
    }

    /** 会话失效原因 → 401 分码；旧令牌无 sid 时调用方直接使用 TOKEN_REVOKED。 */
    public static ErrorCode errorCodeFor(String reason) {
        if (reason == null) {
            return ErrorCode.TOKEN_REVOKED;
        }
        return switch (reason) {
            case UserSession.REASON_KICKED_BY_ADMIN -> ErrorCode.SESSION_KICKED;
            case UserSession.REASON_PASSWORD_CHANGED -> ErrorCode.PASSWORD_CHANGED;
            case UserSession.REASON_ACCOUNT_DISABLED -> ErrorCode.ACCOUNT_DISABLED;
            case UserSession.REASON_USER_DELETED -> ErrorCode.ACCOUNT_DELETED;
            default -> ErrorCode.TOKEN_REVOKED;
        };
    }

    // ------------------------------------------------------------------ 管理端

    public PageResponse<SessionDtos.Response> page(PageQuery pageQuery, Long userId, String keyword, Boolean online) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        UserSessionQuery query = new UserSessionQuery();
        query.setUserId(userId);
        query.setKeyword(keyword);
        query.setOnline(online);
        query.setPage(page);
        query.setPageSize(pageSize);
        PageResult<UserSession> result = sessionRepository.page(query, onlineSince());
        List<SessionDtos.Response> list = result.getList().stream()
                .map(session -> toResponse(session, null))
                .toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    /**
     * 踢单条。只失效目标会话，不触碰 token_version，其他设备不受影响。
     * <p>当前请求所在会话不允许踢（409），应走退出登录。</p>
     */
    @Transactional
    public int kick(Long id, String currentSessionId) {
        UserSession session = sessionRepository.findById(id);
        if (session == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在");
        }
        if (currentSessionId != null && currentSessionId.equals(session.getSessionId())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "不能踢下线当前会话，请使用退出登录");
        }
        return sessionRepository.invalidateById(id, UserSession.REASON_KICKED_BY_ADMIN);
    }

    /** 按用户一键踢全部：包含操作者自己的会话，语义为“该用户全部活跃会话”。 */
    @Transactional
    public int kickUser(Long userId) {
        return sessionRepository.invalidateByUserId(userId, UserSession.REASON_KICKED_BY_ADMIN, null);
    }

    /** 清理失效超过 30 天的会话记录（物理删除）。 */
    @Transactional
    public int cleanInvalidated() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minus(CLEANUP_RETENTION);
        return sessionRepository.deleteInvalidatedBefore(cutoff);
    }

    // ------------------------------------------------------------------ 用户端

    public List<SessionDtos.Response> mine(Long userId, String currentSessionId) {
        return sessionRepository.findActiveByUserId(userId).stream()
                .map(session -> toResponse(session, currentSessionId))
                .toList();
    }

    /** 踢掉自己的其他设备；目标必须是本人会话，当前会话返回 409。 */
    @Transactional
    public int kickMine(Long id, Long userId, String currentSessionId) {
        UserSession session = sessionRepository.findById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在");
        }
        if (currentSessionId != null && currentSessionId.equals(session.getSessionId())) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "不能踢下线当前会话，请使用退出登录");
        }
        return sessionRepository.invalidateById(id, UserSession.REASON_KICKED_BY_ADMIN);
    }

    // ------------------------------------------------------------------ 内部

    private LocalDateTime onlineSince() {
        return LocalDateTime.now(ZoneOffset.UTC).minus(ONLINE_WINDOW);
    }

    private SessionDtos.Response toResponse(UserSession session, String currentSessionId) {
        SessionDtos.Response response = new SessionDtos.Response();
        response.setId(session.getId());
        response.setUserId(session.getUserId());
        response.setUsername(session.getUsername());
        response.setNickname(session.getNickname());
        response.setIp(session.getIp());
        response.setUserAgent(session.getUserAgent());
        response.setDeviceType(session.getDeviceType());
        response.setBrowser(session.getBrowser());
        response.setOs(session.getOs());
        response.setLoginAt(TimeSupport.toInstant(session.getLoginAt()));
        response.setLastActiveAt(TimeSupport.toInstant(session.getLastActiveAt()));
        response.setInvalidatedAt(TimeSupport.toInstant(session.getInvalidatedAt()));
        response.setInvalidateReason(session.getInvalidateReason());
        response.setOnline(!session.isInvalidated()
                && session.getLastActiveAt() != null
                && session.getLastActiveAt().isAfter(onlineSince()));
        if (currentSessionId != null) {
            response.setCurrent(currentSessionId.equals(session.getSessionId()));
        }
        return response;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
