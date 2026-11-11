package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class DeptDtos {

    private DeptDtos() {
    }

    @Data
    public static class SaveRequest {
        private Long parentId;

        @NotBlank(message = "部门名称不能为空")
        @Size(max = 64, message = "部门名称不能超过 64 字")
        private String name;

        private Integer sort;

        @Size(max = 64, message = "负责人不能超过 64 字")
        private String leader;

        @Size(max = 32, message = "联系电话不能超过 32 位")
        private String phone;

        private Boolean isEnabled;
    }

    @Data
    public static class Response {
        private Long id;
        private Long parentId;
        private String ancestors;
        private String name;
        private Integer sort;
        private String leader;
        private String phone;
        private Boolean isEnabled;
        private Instant createdAt;
        private Instant updatedAt;
        private List<Response> children;
    }

    @Data
    public static class Option {
        private Long id;
        private String name;
        private Long parentId;
        private Boolean isEnabled;
        private List<Option> children;
    }
}
