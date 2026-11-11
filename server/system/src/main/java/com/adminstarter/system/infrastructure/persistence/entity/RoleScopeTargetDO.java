package com.adminstarter.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role_scope_target")
public class RoleScopeTargetDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private String targetType;
    private Long targetId;
}
