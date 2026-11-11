package com.adminstarter.system.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLog {

    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private String userAgent;
    private Boolean isSuccess;
    private String message;
    private LocalDateTime createdAt;
}
