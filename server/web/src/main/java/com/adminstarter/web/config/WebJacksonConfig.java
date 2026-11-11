package com.adminstarter.web.config;

import com.adminstarter.web.config.masking.MaskingModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 对外响应专用 ObjectMapper：复制全局配置后只追加掩码模块。
 * 全局 ObjectMapper（缓存、审计、JWT 序列化共用）保持不变，避免掩码值写进缓存或在无上下文时把缓存值隐藏。
 */
@Configuration
@RequiredArgsConstructor
public class WebJacksonConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper maskedMapper = objectMapper.copy().registerModule(new MaskingModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(maskedMapper);
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json")));
        converters.add(0, converter);
    }
}
