package com.adminstarter.web.filter;

import com.adminstarter.auth.infrastructure.AuthTokenStore;
import com.adminstarter.auth.infrastructure.JwtTokenProvider;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.AuthUserQueryService;
import com.adminstarter.system.application.UserSessionAppService;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.model.UserSession;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 从 HttpOnly Cookie 读取 access token，写入 SecurityContext 与数据权限上下文。
 * <p>每请求按库中现状重建用户快照（启用状态、部门、角色、权限码），并校验令牌版本；
 * 失败原因写入请求属性，由 SecurityConfig 的入口点输出对应 401 分码。公开接口不因此短路。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    public static final String REFRESH_COOKIE = "REFRESH_TOKEN";
    /** 认证失败原因，由 SecurityConfig.authenticationEntryPoint 读取。 */
    public static final String AUTH_ERROR_ATTRIBUTE = "app.auth.error";

    private final JwtTokenProvider tokenProvider;
    private final AuthTokenStore tokenStore;
    private final AuthUserQueryService authUserQueryService;
    private final UserSessionAppService sessionAppService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = readCookie(request, ACCESS_COOKIE);
            if (token != null) {
                authenticate(request, token);
            }
            chain.doFilter(request, response);
        } finally {
            DataScopeContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Claims claims = tokenProvider.parse(token);
            if (!JwtTokenProvider.TYPE_ACCESS.equals(claims.get("type", String.class))) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
                return;
            }
            if (tokenStore.isAccessDenied(tokenProvider.jti(claims))) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_REVOKED);
                return;
            }

            User user = authUserQueryService.findById(Long.valueOf(claims.getSubject()));
            if (user == null) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.ACCOUNT_DELETED);
                return;
            }
            if (!Boolean.TRUE.equals(user.getIsEnabled())) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.ACCOUNT_DISABLED);
                return;
            }
            long currentVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
            if (tokenProvider.tokenVersion(claims) != currentVersion) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, revokedReason(user));
                return;
            }

            // 设备级校验：旧令牌无 sid → 失效；会话不存在/不属于该用户 → 失效；已失效 → 按原因分码
            String sessionId = tokenProvider.sessionId(claims);
            UserSession session = sessionAppService.findBySessionId(sessionId);
            if (sessionId == null || session == null || !session.getUserId().equals(user.getId())) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_REVOKED);
                return;
            }
            if (session.isInvalidated()) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE,
                        UserSessionAppService.errorCodeFor(session.getInvalidateReason()));
                return;
            }
            sessionAppService.touch(sessionId);

            CurrentUser currentUser = CurrentUser.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .deptId(user.getDeptId())
                    .sessionId(sessionId)
                    .roles(new LinkedHashSet<>(authUserQueryService.roleCodes(user.getId())))
                    .permissions(new LinkedHashSet<>(authUserQueryService.permissionCodes(user.getId())))
                    .build();
            List<SimpleGrantedAuthority> authorities = currentUser.getPermissions().stream()
                    .map(SimpleGrantedAuthority::new).toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            DataScopeContext.set(currentUser);
        } catch (BizException exception) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, exception.getErrorCode());
        } catch (RuntimeException ignored) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
        }
    }

    private ErrorCode revokedReason(User user) {
        return switch (user.getTokenRevokeReason() == null ? "" : user.getTokenRevokeReason()) {
            case "PASSWORD_RESET" -> ErrorCode.PASSWORD_RESET;
            case "PASSWORD_CHANGED" -> ErrorCode.PASSWORD_CHANGED;
            default -> ErrorCode.TOKEN_REVOKED;
        };
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
