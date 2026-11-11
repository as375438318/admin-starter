package com.adminstarter.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthTokens {

    private String accessToken;
    private String refreshToken;
    private long accessMaxAgeSeconds;
    private long refreshMaxAgeSeconds;
    private Long userId;
    private String username;
    private String nickname;
}
