package com.adminstarter.web.controller;

import com.adminstarter.auth.application.AuthAppService;
import com.adminstarter.auth.application.dto.AuthTokens;
import com.adminstarter.auth.application.dto.LoginRequest;
import com.adminstarter.auth.application.dto.LoginResponse;
import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.IpUtils;
import com.adminstarter.system.application.MenuAppService;
import com.adminstarter.system.application.ProfileAppService;
import com.adminstarter.system.application.UserSessionAppService;
import com.adminstarter.system.application.dto.MenuDtos;
import com.adminstarter.system.application.dto.ProfileDtos;
import com.adminstarter.system.application.dto.SessionDtos;
import com.adminstarter.web.auth.AuthCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证入口：登录、刷新、登出，以及当前用户路由与权限码。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthAppService authAppService;
    private final AuthCookieService cookieService;
    private final MenuAppService menuAppService;
    private final ProfileAppService profileAppService;
    private final UserSessionAppService sessionAppService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest,
                                            HttpServletResponse servletResponse) {
        AuthTokens tokens = authAppService.login(request, IpUtils.clientIp(servletRequest),
                servletRequest.getHeader("User-Agent"));
        cookieService.writeTokens(servletResponse, tokens);
        return ApiResponse.ok(toLoginResponse(tokens));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest servletRequest,
                                              HttpServletResponse servletResponse) {
        AuthTokens tokens = authAppService.refresh(readCookie(servletRequest, AuthCookieService.REFRESH_COOKIE));
        cookieService.writeTokens(servletResponse, tokens);
        return ApiResponse.ok(toLoginResponse(tokens));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        authAppService.logout(readCookie(servletRequest, AuthCookieService.ACCESS_COOKIE),
                readCookie(servletRequest, AuthCookieService.REFRESH_COOKIE));
        cookieService.clear(servletResponse);
        return ApiResponse.ok();
    }

    @GetMapping("/routes")
    public ApiResponse<List<MenuDtos.Route>> routes() {
        return ApiResponse.ok(menuAppService.routes(currentUser().getUserId()));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<String>> permissions() {
        return ApiResponse.ok(menuAppService.permissions(currentUser().getUserId()));
    }

    @GetMapping("/me")
    public ApiResponse<ProfileDtos.Response> me() {
        return ApiResponse.ok(profileAppService.profile());
    }

    /** 我的登录设备：只返回当前用户的活跃会话，current 标记本次请求所在会话。 */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionDtos.Response>> mySessions() {
        CurrentUser current = currentUser();
        return ApiResponse.ok(sessionAppService.mine(current.getUserId(), current.getSessionId()));
    }

    /** 踢掉自己的其他设备：目标必须是本人会话，当前会话走退出登录。 */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<SessionDtos.AffectResponse> kickMySession(@PathVariable Long id) {
        CurrentUser current = currentUser();
        int affected = sessionAppService.kickMine(id, current.getUserId(), current.getSessionId());
        return ApiResponse.ok(new SessionDtos.AffectResponse(affected));
    }

    private LoginResponse toLoginResponse(AuthTokens tokens) {
        return new LoginResponse(tokens.getUserId(), tokens.getUsername(), tokens.getNickname(),
                tokens.getAccessMaxAgeSeconds(), tokens.getRefreshMaxAgeSeconds());
    }

    private CurrentUser currentUser() {
        CurrentUser user = DataScopeContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user;
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
