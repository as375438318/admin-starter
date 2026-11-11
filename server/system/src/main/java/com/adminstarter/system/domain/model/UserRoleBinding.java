package com.adminstarter.system.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRoleBinding {

    private Long userId;
    private Long roleId;
    private String roleName;
}
