package com.adminstarter.system.application.dto;

import lombok.Data;

/**
 * 公开配置：只承载可在未登录页面展示的字段，禁止加入敏感参数。
 */
public final class PublicConfigDtos {

    private PublicConfigDtos() {
    }

    @Data
    public static class Response {
        private String name;
        private String slogan;
        private String copyright;
        private String icp;
        /** 单个文件上传上限（字节），对应 sys.upload.max-size，供前端校验与提示 */
        private Long uploadMaxSize;
    }
}
