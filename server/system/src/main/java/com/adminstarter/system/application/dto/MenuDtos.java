package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class MenuDtos {

    private MenuDtos() {
    }

    @Data
    public static class SaveRequest {
        private Long parentId;

        @NotBlank(message = "菜单名称不能为空")
        @Size(max = 64, message = "菜单名称不能超过 64 字")
        private String name;

        @NotBlank(message = "菜单类型不能为空")
        @Pattern(regexp = "^[MCF]$", message = "菜单类型只能是 M/C/F")
        private String menuType;

        @Size(max = 255, message = "路由路径不能超过 255 字")
        private String path;

        @Size(max = 255, message = "组件路径不能超过 255 字")
        private String component;

        @Size(max = 128, message = "权限标识不能超过 128 字")
        private String permissionCode;

        @Size(max = 64, message = "图标不能超过 64 字")
        private String icon;
        private Integer sort;
        private Boolean isVisible;
        private Boolean isEnabled;
    }

    @Data
    public static class Response {
        private Long id;
        private Long parentId;
        private String name;
        private String menuType;
        private String path;
        private String component;
        private String permissionCode;
        private String icon;
        private Integer sort;
        private Boolean isVisible;
        private Boolean isEnabled;
        private Instant createdAt;
        private Instant updatedAt;
        private List<Response> children;
    }

    @Data
    public static class Route {
        private Long id;
        private String path;
        private String name;
        private String component;
        private RouteMeta meta;
        private List<Route> children;
    }

    @Data
    public static class RouteMeta {
        private String title;
        private String icon;
        private Boolean hidden;
        private String permission;
    }
}
