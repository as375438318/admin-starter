package com.adminstarter.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private Long userId;
    private String username;
    private String nickname;
    private long accessExpiresIn;
    private long refreshExpiresIn;
    /** true = 必须先修改密码才能使用其他功能（等保 P0-1 首登强制改密）。 */
    private boolean mustChangePassword;
    /** true = 口令已超过有效期。 */
    private boolean passwordExpired;
}
