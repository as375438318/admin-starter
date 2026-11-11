package com.adminstarter.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** HS256 密钥，生产必须由环境变量注入且不少于 32 字节。 */
    private String secret = "";

    private Duration accessTtl = Duration.ofMinutes(15);

    private Duration refreshTtl = Duration.ofDays(7);

    /** Cookie Secure 属性；本地 http 开发可显式关闭，生产必须开启。 */
    private boolean cookieSecure = true;

    private String cookieSameSite = "Lax";

    private int maxLoginFailures = 5;

    private Duration lockDuration = Duration.ofMinutes(15);
}
