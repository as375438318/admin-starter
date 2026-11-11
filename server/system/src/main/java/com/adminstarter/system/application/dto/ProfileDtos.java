package com.adminstarter.system.application.dto;

import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.common.masking.MaskStatesSupport;
import com.adminstarter.common.masking.MaskedField;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    @Data
    public static class Response extends MaskStatesSupport {
        private Long userId;
        private String username;
        private String nickname;

        @MaskedField(value = "user:email", format = MaskFormat.EMAIL)
        private String email;

        @MaskedField(value = "user:phone", format = MaskFormat.PHONE)
        private String phone;

        private Long deptId;
        private String deptName;
        private List<String> roles;
        private List<String> permissions;
    }

    @Data
    public static class UpdateRequest {
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名不能超过 64 字")
        private String nickname;

        @Email(message = "邮箱格式不正确")
        @Size(max = 64, message = "邮箱不能超过 64 位")
        private String email;

        @Size(max = 32, message = "手机号不能超过 32 位")
        private String phone;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        @Size(max = 128, message = "原密码长度不能超过 128 位")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "新密码长度需为 8-64 位")
        private String newPassword;
    }
}
