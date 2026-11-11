package com.adminstarter.web.filter;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Cookie 载体下的 CSRF 防护：写操作校验来源（`CT-AUTH-004`）。
 *
 * <p>判定顺序：</p>
 * <ol>
 *   <li>安全方法（GET/HEAD/OPTIONS）直接放行；</li>
 *   <li>取 {@code Origin}；缺失时回落 {@code Referer} 的同源部分；</li>
 *   <li>仍无来源信息时：**只有不携带认证 Cookie 的请求放行**——CSRF 的前提是浏览器自动附带凭据，
 *       没有凭据就没有 CSRF 面（例如登录接口、服务间无凭据调用）；带了凭据却说不出来源，一律拒绝；</li>
 *   <li>来源必须在允许清单内。</li>
 * </ol>
 *
 * <p>拒绝时返回统一信封并写 {@code X-Trace-Id}，与 `CT-TRACE-002` 一致（响应头与响应体 traceId 相同）。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final List<String> SAFE_METHODS = List.of("GET", "HEAD", "OPTIONS");
    private static final List<String> AUTH_COOKIES = List.of("ACCESS_TOKEN", "REFRESH_TOKEN");

    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public OriginCheckFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            chain.doFilter(request, response);
            return;
        }

        List<String> allowed = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        String origin = normalize(request.getHeader("Origin"));
        if (origin == null) {
            origin = originOf(request.getHeader("Referer"));
        }

        if (origin == null) {
            if (!carriesAuthCookie(request)) {
                chain.doFilter(request, response);
                return;
            }
            reject(response);
            return;
        }
        if (!allowed.contains(origin)) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 空值与字面量 "null"（沙箱 iframe 等）都视为“说不出来源”。 */
    private String normalize(String origin) {
        if (origin == null) {
            return null;
        }
        String trimmed = origin.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String originOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
            return defaultPort
                    ? uri.getScheme() + "://" + uri.getHost()
                    : uri.getScheme() + "://" + uri.getHost() + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean carriesAuthCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIES.contains(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response) throws IOException {
        String traceId = MDC.get("traceId");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader("X-Trace-Id", traceId);
        }
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.FORBIDDEN.getCode(), "来源校验失败", null);
        body.setTraceId(traceId);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
