package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

public final class PostDtos {

    private PostDtos() {
    }

    @Data
    public static class SaveRequest {
        @NotNull(message = "所属部门不能为空")
        private Long deptId;

        @NotBlank(message = "岗位编码不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{2,64}$", message = "岗位编码需为 2-64 位字母、数字或下划线")
        private String code;

        @NotBlank(message = "岗位名称不能为空")
        @Size(max = 64, message = "岗位名称不能超过 64 字")
        private String name;

        private Integer sort;
        private Boolean isEnabled;
    }

    @Data
    public static class Response {
        private Long id;
        private Long deptId;
        private String deptName;
        private String code;
        private String name;
        private Integer sort;
        private Boolean isEnabled;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class ImportResult {
        private long totalRows;
        private int imported;
    }

    @Data
    public static class ExportAccepted {
        private String mode = "ASYNC";
        private Long taskId;
        private long totalRows;
    }
}
