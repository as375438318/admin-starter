package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.system.application.PublicConfigAppService;
import com.adminstarter.system.application.dto.PublicConfigDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开配置：未登录可访问，仅返回白名单字段（登录页启动注入用）。
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConfigController {

    private final PublicConfigAppService publicConfigAppService;

    @GetMapping("/configs")
    public ApiResponse<PublicConfigDtos.Response> configs() {
        return ApiResponse.ok(publicConfigAppService.current());
    }
}
