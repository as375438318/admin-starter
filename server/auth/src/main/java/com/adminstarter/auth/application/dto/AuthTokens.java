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
    /** 下次登录必须修改密码（管理员重置、初始弱口令、口令过期）。 */
    private boolean mustChangePassword;
    /** 口令已过期（前端据此提示"密码已过期，请修改"）。 */
    private boolean passwordExpired;
}
