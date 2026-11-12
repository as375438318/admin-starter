package com.adminstarter.system.infrastructure.persistence.entity;

import com.adminstarter.system.infrastructure.persistence.DataPermissionExempt;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@DataPermissionExempt(reason = "用户管理仅由功能权限拦截；按部门查看用户走列表普通筛选参数 deptId，不进入数据权限层")
public class UserDO extends BaseDO {

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
    private LocalDateTime passwordUpdatedAt;
    private Boolean mustChangePassword;
}
