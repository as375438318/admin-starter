package com.adminstarter.system.application.dto;

import lombok.Data;

import java.time.Instant;

public final class SessionDtos {

    private SessionDtos() {
    }

    @Data
    public static class Response {
        private Long id;
        private Long userId;
        private String username;
        private String nickname;
        private String ip;
        private String userAgent;
        private String deviceType;
        private String browser;
        private String os;
        private Instant loginAt;
        private Instant lastActiveAt;
        private Instant invalidatedAt;
        private String invalidateReason;
        /** 在线口径：未失效且最近 5 分钟内有请求。 */
        private Boolean online;
        /** 仅用户端返回：是否为本次请求所在会话。 */
        private Boolean current;
    }

    @Data
    public static class AffectResponse {
        private int affected;

        public AffectResponse(int affected) {
            this.affected = affected;
        }
    }
}
