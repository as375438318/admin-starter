package com.adminstarter.system.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

/** 客户档案的请求/响应模型。 */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    @Data
    public static class SaveRequest {
        @NotBlank(message = "客户名称不能为空")
        @Size(max = 100, message = "客户名称过长")
        private String name;

        @Size(max = 16, message = "客户等级过长")
        private String level;

        @Size(max = 64, message = "联系人过长")
        private String contact;

        @Size(max = 32, message = "联系电话过长")
        private String phone;

        private Boolean isEnabled;

        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String level;
        private String contact;
        private String phone;
        private Boolean isEnabled;
        private String remark;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
