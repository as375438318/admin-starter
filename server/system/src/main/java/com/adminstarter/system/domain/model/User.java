package com.adminstarter.system.domain.model;

import java.time.LocalDateTime;
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
    /** 密码最近修改时间；null 表示历史数据从未记录（按已过期处理）。 */
    private LocalDateTime passwordUpdatedAt;
    /** true = 下次登录必须修改密码（管理员重置、初始弱口令、口令过期后置位）。 */
    private Boolean mustChangePassword;
}
