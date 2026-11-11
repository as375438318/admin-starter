package com.adminstarter.web.auth;

import com.adminstarter.auth.application.dto.AuthTokens;
import com.adminstarter.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 令牌 Cookie 下发与清除统一由服务端完成（CT-AUTH-002）。
 * <p>ACCESS_TOKEN 路径为 /；REFRESH_TOKEN 只随 /api/v1/auth 下请求发送。</p>
 */
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "ACCESS_TOKEN";
    public static final String REFRESH_COOKIE = "REFRESH_TOKEN";

    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthProperties properties;

    public void writeTokens(HttpServletResponse response, AuthTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE, tokens.getAccessToken(), ACCESS_PATH,
                Duration.ofSeconds(tokens.getAccessMaxAgeSeconds())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, tokens.getRefreshToken(), REFRESH_PATH,
                Duration.ofSeconds(tokens.getRefreshMaxAgeSeconds())).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(ACCESS_COOKIE, "", ACCESS_PATH, Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO).toString());
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
