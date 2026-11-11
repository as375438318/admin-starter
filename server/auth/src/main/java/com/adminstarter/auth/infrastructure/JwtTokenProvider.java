package com.adminstarter.auth.infrastructure;

import com.adminstarter.auth.config.AuthProperties;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 签发与解析。access/refresh 用 type 区分，refresh 另在缓存白名单登记。
 */
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final AuthProperties properties;

    public JwtTokenProvider(AuthProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.auth.secret 未配置或长度不足 32 字节，拒绝启动");
        }
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(CurrentUser user, String jti, long tokenVersion, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("type", TYPE_ACCESS)
                .claim("jti", jti)
                .claim("ver", tokenVersion)
                .claim("sid", sessionId)
                .claim("username", user.getUsername())
                .claim("deptId", user.getDeptId())
                .claim("roles", user.getRoles())
                .claim("perms", user.getPermissions())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTtl())))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(Long userId, String jti, long tokenVersion, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TYPE_REFRESH)
                .claim("jti", jti)
                .claim("ver", tokenVersion)
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTtl())))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "登录状态已过期");
        }
    }

    public String jti(Claims claims) {
        return claims.get("jti", String.class);
    }

    public long tokenVersion(Claims claims) {
        Number version = claims.get("ver", Number.class);
        return version == null ? 0L : version.longValue();
    }

    /** 会话标识；旧令牌无该 claim 时返回 null，调用方按“登录状态已失效”处理。 */
    public String sessionId(Claims claims) {
        return claims.get("sid", String.class);
    }

    public Duration remaining(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return Duration.ZERO;
        }
        long millis = expiration.getTime() - System.currentTimeMillis();
        return millis <= 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    public CurrentUser currentUser(Claims claims) {
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> perms = claims.get("perms", List.class);
        Number deptId = claims.get("deptId", Number.class);
        return CurrentUser.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .username(claims.get("username", String.class))
                .deptId(deptId == null ? null : deptId.longValue())
                .roles(roles == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(roles))
                .permissions(perms == null ? java.util.Set.of() : new java.util.LinkedHashSet<>(perms))
                .build();
    }

    public String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public long accessMaxAgeSeconds() {
        return properties.getAccessTtl().toSeconds();
    }

    public long refreshMaxAgeSeconds() {
        return properties.getRefreshTtl().toSeconds();
    }
}
