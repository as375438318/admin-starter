package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

public final class ConfigDtos {

    private ConfigDtos() {
    }

    @Data
    public static class SaveRequest {
        @NotBlank(message = "参数键不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]{2,128}$", message = "参数键需为 2-128 位字母、数字、点、下划线或短横线")
        private String configKey;

        @NotBlank(message = "参数值不能为空")
        @Size(max = 2000, message = "参数值不能超过 2000 字")
        private String configValue;

        @NotBlank(message = "参数名称不能为空")
        @Size(max = 64, message = "参数名称不能超过 64 字")
        private String name;

        @Size(max = 255, message = "备注不能超过 255 字")
        private String remark;
        private Boolean isSensitive;
        private Boolean isEnabled;
    }

    @Data
    public static class Response {
        private Long id;
        private String configKey;
        private String configValue;
        private String name;
        private String remark;
        private Boolean isSensitive;
        private Boolean isEnabled;
        private Instant createdAt;
        private Instant updatedAt;
        /** 生效方式：HOT（改完立即生效）/ STARTUP（需重启）。判定见 ConfigEffects。 */
        private String effect;
        /** effect == HOT 的便捷布尔，便于前端直接绑定提示。 */
        private Boolean hotReloadable;
    }
}
