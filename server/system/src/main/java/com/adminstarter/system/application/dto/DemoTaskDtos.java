package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

public final class DemoTaskDtos {

    private DemoTaskDtos() {
    }

    @Data
    public static class SaveRequest {
        @NotBlank(message = "任务标题不能为空")
        @Size(max = 128, message = "任务标题不能超过 128 字")
        private String title;

        @Pattern(regexp = "TODO|DOING|DONE", message = "任务状态只能是 TODO / DOING / DONE")
        private String status;

        @NotNull(message = "归属部门不能为空")
        private Long deptId;

        /** 负责人；不填时默认为当前登录人。 */
        private Long ownerId;
    }

    @Data
    public static class Response {
        private Long id;
        private String title;
        private String status;
        private Long deptId;
        private String deptName;
        private Long ownerId;
        private String ownerName;
        private Long createBy;
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
