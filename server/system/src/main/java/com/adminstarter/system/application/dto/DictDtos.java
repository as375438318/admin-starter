package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class DictDtos {

    private DictDtos() {
    }

    @Data
    public static class TypeSaveRequest {
        @NotBlank(message = "字典名称不能为空")
        @Size(max = 64, message = "字典名称不能超过 64 字")
        private String name;

        @NotBlank(message = "字典类型不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{2,64}$", message = "字典类型需为 2-64 位字母、数字、下划线或短横线")
        private String type;

        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
        private Boolean isEnabled;
    }

    @Data
    public static class ItemSaveRequest {
        @NotNull(message = "所属字典不能为空")
        private Long dictTypeId;

        @NotBlank(message = "字典标签不能为空")
        @Size(max = 64, message = "字典标签不能超过 64 字")
        private String label;

        @NotBlank(message = "字典值不能为空")
        @Size(max = 64, message = "字典值不能超过 64 字")
        private String value;

        private Integer sort;
        private Boolean isEnabled;

        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
    }

    @Data
    public static class TypeResponse {
        private Long id;
        private String name;
        private String type;
        private String remark;
        private Boolean isEnabled;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class ItemResponse {
        private Long id;
        private Long dictTypeId;
        private String label;
        private String value;
        private Integer sort;
        private Boolean isEnabled;
        private String remark;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class TypeBundle {
        private String type;
        private String name;
        private List<ItemResponse> items;
    }
}
