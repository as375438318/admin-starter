package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseModel {

    private String username;
    private String nickname;
    private String password;
    private Long deptId;
    private String email;
    private String phone;
    private Boolean isEnabled;
    private Long tokenVersion;
    private String tokenRevokeReason;
    private Long createBy;
}
