package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.ConfigAppService;
import com.adminstarter.system.application.dto.ConfigDtos;
import com.adminstarter.web.log.OperLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigAppService configAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:config:list')")
    public ApiResponse<PageResponse<ConfigDtos.Response>> page(PageQuery pageQuery,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Boolean isSensitive) {
        return ApiResponse.ok(configAppService.page(pageQuery, keyword, isSensitive));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:list')")
    public ApiResponse<ConfigDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(configAppService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:config:create')")
    @OperLogRecord(module = "系统参数", action = "新增参数")
    public ApiResponse<ConfigDtos.Response> create(@Valid @RequestBody ConfigDtos.SaveRequest request) {
        return ApiResponse.ok(configAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:update')")
    @OperLogRecord(module = "系统参数", action = "修改参数")
    public ApiResponse<ConfigDtos.Response> update(@PathVariable Long id,
                                                  @Valid @RequestBody ConfigDtos.SaveRequest request) {
        return ApiResponse.ok(configAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:config:delete')")
    @OperLogRecord(module = "系统参数", action = "删除参数")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        configAppService.delete(id);
        return ApiResponse.ok();
    }
}
