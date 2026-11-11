package com.adminstarter.system.application.dto;

import com.adminstarter.system.application.validation.EnumValue;
import com.adminstarter.system.domain.enums.DataScope;
import com.adminstarter.system.domain.enums.OwnFilter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class RoleDtos {

    private RoleDtos() {
    }

    @Data
    public static class SaveRequest {
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称不能超过 64 字")
        private String name;

        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{2,64}$", message = "角色编码需为 2-64 位字母、数字或下划线")
        private String code;

        private Integer sort;

        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;

        private Boolean isEnabled;
    }

    @Data
    public static class DataScopeRequest {
        @NotBlank(message = "组织范围不能为空")
        @EnumValue(enumClass = DataScope.class, message = "数据范围取值不合法")
        private String dataScope;

        @NotBlank(message = "归属过滤不能为空")
        @EnumValue(enumClass = OwnFilter.class, message = "归属过滤取值不合法")
        private String ownFilter;

        private List<Long> deptIds;

        private List<Long> userIds;
    }

    @Data
    public static class AssignMenuRequest {
        @NotNull(message = "菜单列表不能为空")
        private List<Long> menuIds;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String code;
        private Integer sort;
        private String dataScope;
        private String ownFilter;
        private String remark;
        private Boolean isEnabled;
        private List<Long> menuIds;
        private List<Long> deptIds;
        private List<Long> userIds;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
