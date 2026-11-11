package com.adminstarter.web.config.masking;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * 掩码模块：只注册到 Web 响应专用 ObjectMapper，禁止注册到全局 ObjectMapper。
 */
public class MaskingModule extends SimpleModule {

    public MaskingModule() {
        super("adminMaskingModule");
        setSerializerModifier(new MaskingSerializerModifier());
    }
}
