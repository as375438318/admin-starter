package com.adminstarter.system.application.dto;

import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.common.masking.MaskStatesSupport;
import com.adminstarter.common.masking.MaskedField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    @Data
    public static class CreateRequest {
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "账号需为 4-32 位字母、数字或下划线")
        private String username;

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 字")
        private String nickname;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        private String password;

        private Long deptId;

        @Email(message = "邮箱格式不正确")
        @Size(max = 64, message = "邮箱不能超过 64 位")
        private String email;

        @Size(max = 32, message = "手机号不能超过 32 位")
        private String phone;

        private List<Long> roleIds;
    }

    @Data
    public static class UpdateRequest {
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 字")
        private String nickname;

        private Long deptId;

        @Email(message = "邮箱格式不正确")
        @Size(max = 64, message = "邮箱不能超过 64 位")
        private String email;

        @JsonIgnore
        private boolean emailSpecified;

        @Size(max = 32, message = "手机号不能超过 32 位")
        private String phone;

        @JsonIgnore
        private boolean phoneSpecified;

        private List<Long> roleIds;

        @JsonSetter("email")
        public void setEmail(String email) {
            this.email = email;
            this.emailSpecified = true;
        }

        @JsonSetter("phone")
        public void setPhone(String phone) {
            this.phone = phone;
            this.phoneSpecified = true;
        }
    }

    @Data
    public static class StatusRequest {
        @NotNull(message = "启用状态不能为空")
        private Boolean isEnabled;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        private String password;
    }

    @Data
    public static class AssignRolesRequest {
        @NotNull(message = "角色列表不能为空")
        private List<Long> roleIds;
    }

    @Data
    public static class Response extends MaskStatesSupport {
        private Long id;
        private String username;
        private String nickname;
        private Long deptId;
        private String deptName;

        @MaskedField(value = "user:email", format = MaskFormat.EMAIL)
        private String email;

        @MaskedField(value = "user:phone", format = MaskFormat.PHONE)
        private String phone;

        private Boolean isEnabled;
        private List<Long> roleIds;
        private List<String> roleNames;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    public static class Option {
        private Long id;
        private String username;
        private String nickname;
        private Long deptId;
        private String deptName;
    }
}
