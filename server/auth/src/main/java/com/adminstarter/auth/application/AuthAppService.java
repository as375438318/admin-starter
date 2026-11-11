package com.adminstarter.auth.application;

import com.adminstarter.auth.application.dto.AuthTokens;
import com.adminstarter.auth.application.dto.LoginRequest;
import com.adminstarter.auth.config.AuthProperties;
import com.adminstarter.auth.infrastructure.AuthTokenStore;
import com.adminstarter.auth.infrastructure.JwtTokenProvider;
import com.adminstarter.common.cache.CacheService;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.system.application.AuthUserQueryService;
import com.adminstarter.system.application.UserSessionAppService;
import com.adminstarter.system.domain.model.LoginLog;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.model.UserSession;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthAppService {

    private static final String FAIL_PREFIX = com.adminstarter.common.cache.CacheNamespaces.AUTH_FAIL_PREFIX;

    private final AuthUserQueryService authUserQueryService;
    private final UserSessionAppService sessionAppService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthTokenStore tokenStore;
    private final CacheService cacheService;
    private final AuthProperties properties;

    public AuthTokens login(LoginRequest request, String ip, String userAgent) {
        String failKey = FAIL_PREFIX + request.getUsername();
        if (isLocked(failKey)) {
            throw new BizException(ErrorCode.RATE_LIMITED, "登录失败次数过多，请稍后再试");
        }

        User user = authUserQueryService.findByUsername(request.getUsername());
        if (user == null) {
            bumpFailure(failKey);
            recordLogin(null, request.getUsername(), ip, userAgent, false, "账号或密码错误");
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            recordLogin(user, user.getUsername(), ip, userAgent, false, "账号已停用");
            // 与「每请求校验」保持同一分码：停用 = 401 + 20103，前端据此展示「账号已被停用」并终止（不再尝试刷新）
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, "账号已被停用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            bumpFailure(failKey);
            recordLogin(user, user.getUsername(), ip, userAgent, false, "密码错误");
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        cacheService.delete(failKey);
        CurrentUser currentUser = buildCurrentUser(user);
        UserSession session = sessionAppService.openSession(user.getId(), user.getUsername(), ip, userAgent);
        AuthTokens tokens = issueTokens(currentUser, versionOf(user), session.getSessionId());
        tokens.setNickname(user.getNickname());
        recordLogin(user, user.getUsername(), ip, userAgent, true, "登录成功");
        return tokens;
    }

    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少刷新令牌");
        }
        Claims claims = tokenProvider.parse(refreshToken);
        if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌类型不正确");
        }
        String jti = tokenProvider.jti(claims);
        if (!tokenStore.isRefreshActive(jti)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = authUserQueryService.findById(userId);
        if (user == null) {
            tokenStore.revokeRefresh(jti);
            throw new BizException(ErrorCode.ACCOUNT_DELETED, ErrorCode.ACCOUNT_DELETED.getMessage());
        }
        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            tokenStore.revokeRefresh(jti);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, ErrorCode.ACCOUNT_DISABLED.getMessage());
        }
        if (tokenProvider.tokenVersion(claims) != versionOf(user)) {
            tokenStore.revokeRefresh(jti);
            throw revokedException(user);
        }

        // 设备级校验：会话必须存在、属于该用户且未失效；被踢/主动退出后 refresh 同口径失效
        String sessionId = tokenProvider.sessionId(claims);
        UserSession session = sessionAppService.findBySessionId(sessionId);
        if (sessionId == null || session == null || !session.getUserId().equals(userId)) {
            tokenStore.revokeRefresh(jti);
            throw new BizException(ErrorCode.TOKEN_REVOKED, ErrorCode.TOKEN_REVOKED.getMessage());
        }
        if (session.isInvalidated()) {
            tokenStore.revokeRefresh(jti);
            ErrorCode code = UserSessionAppService.errorCodeFor(session.getInvalidateReason());
            throw new BizException(code, code.getMessage());
        }
        sessionAppService.touch(sessionId);

        // 旋转刷新令牌，旧令牌立即失效
        tokenStore.revokeRefresh(jti);
        CurrentUser currentUser = buildCurrentUser(user);
        AuthTokens tokens = issueTokens(currentUser, versionOf(user), sessionId);
        tokens.setNickname(user.getNickname());
        return tokens;
    }

    /**
     * 本人改密后为当前设备重签：版本已在库中自增，旧版本令牌（其他设备）全部失效，当前设备换发新版本。
     * <p>sid 保持不变，会话记录继续有效；其他会话已由 ProfileAppService 标记 PASSWORD_CHANGED。</p>
     */
    public AuthTokens reissueAfterPasswordChange(Long userId, String sessionId) {
        User user = authUserQueryService.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.ACCOUNT_DELETED, ErrorCode.ACCOUNT_DELETED.getMessage());
        }
        if (!Boolean.TRUE.equals(user.getIsEnabled())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED, ErrorCode.ACCOUNT_DISABLED.getMessage());
        }
        return issueTokens(buildCurrentUser(user), versionOf(user), sessionId);
    }

    public void logout(String accessToken, String refreshToken) {
        String sessionId = null;
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = tokenProvider.parse(accessToken);
                sessionId = tokenProvider.sessionId(claims);
                tokenStore.denyAccess(tokenProvider.jti(claims), tokenProvider.remaining(claims));
            } catch (RuntimeException ignored) {
                // access 已过期时无需拉黑
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims claims = tokenProvider.parse(refreshToken);
                if (sessionId == null) {
                    sessionId = tokenProvider.sessionId(claims);
                }
                tokenStore.revokeRefresh(tokenProvider.jti(claims));
            } catch (RuntimeException ignored) {
                // refresh 失效时忽略
            }
        }
        // 主动退出只失效当前会话，不触碰 token_version，其他设备继续可用
        if (sessionId != null) {
            sessionAppService.invalidateBySessionId(sessionId, UserSession.REASON_LOGOUT);
        }
    }

    private AuthTokens issueTokens(CurrentUser currentUser, long tokenVersion, String sessionId) {
        String accessJti = tokenProvider.newJti();
        String accessToken = tokenProvider.createAccessToken(currentUser, accessJti, tokenVersion, sessionId);

        String refreshJti = tokenProvider.newJti();
        String refreshToken = tokenProvider.createRefreshToken(currentUser.getUserId(), refreshJti, tokenVersion,
                sessionId);
        tokenStore.saveRefresh(refreshJti, currentUser.getUserId(), Duration.ofSeconds(tokenProvider.refreshMaxAgeSeconds()));

        return new AuthTokens(accessToken, refreshToken, tokenProvider.accessMaxAgeSeconds(),
                tokenProvider.refreshMaxAgeSeconds(), currentUser.getUserId(), currentUser.getUsername(), null);
    }

    private long versionOf(User user) {
        return user.getTokenVersion() == null ? 0L : user.getTokenVersion();
    }

    private BizException revokedException(User user) {
        ErrorCode code = switch (user.getTokenRevokeReason() == null ? "" : user.getTokenRevokeReason()) {
            case "PASSWORD_RESET" -> ErrorCode.PASSWORD_RESET;
            case "PASSWORD_CHANGED" -> ErrorCode.PASSWORD_CHANGED;
            default -> ErrorCode.TOKEN_REVOKED;
        };
        return new BizException(code, code.getMessage());
    }

    private CurrentUser buildCurrentUser(User user) {
        List<String> roles = authUserQueryService.roleCodes(user.getId());
        List<String> permissions = authUserQueryService.permissionCodes(user.getId());
        return CurrentUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .deptId(user.getDeptId())
                .roles(new LinkedHashSet<>(roles))
                .permissions(new LinkedHashSet<>(permissions))
                .build();
    }

    private boolean isLocked(String failKey) {
        String count = cacheService.getString(failKey);
        return count != null && Long.parseLong(count) >= properties.getMaxLoginFailures();
    }

    private void bumpFailure(String failKey) {
        cacheService.increment(failKey, properties.getLockDuration());
    }

    private void recordLogin(User user, String username, String ip, String userAgent, boolean success, String message) {
        try {
            LoginLog log = new LoginLog();
            log.setUserId(user == null ? null : user.getId());
            log.setUsername(user == null ? username : user.getUsername());
            log.setIp(ip);
            log.setUserAgent(userAgent);
            log.setIsSuccess(success);
            log.setMessage(message);
            authUserQueryService.recordLogin(log);
        } catch (RuntimeException ignored) {
            // 登录审计写入失败不阻断主流程
        }
    }
}
