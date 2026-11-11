package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.OperLogAppService;
import com.adminstarter.system.application.dto.LogDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogAppService operLogAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:log:list')")
    public ApiResponse<PageResponse<LogDtos.OperLogResponse>> page(
            PageQuery pageQuery,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer resultCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(operLogAppService.page(pageQuery, keyword, resultCode, from, to));
    }
}
