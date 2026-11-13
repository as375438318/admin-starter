package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.CustomerAppService;
import com.adminstarter.system.application.dto.CustomerDtos;
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
@RequestMapping("/api/v1/demo/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerAppService customerAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('demo:customer:list')")
    public ApiResponse<PageResponse<CustomerDtos.Response>> page(PageQuery pageQuery,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(customerAppService.page(pageQuery, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('demo:customer:list')")
    public ApiResponse<CustomerDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(customerAppService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('demo:customer:create')")
    @OperLogRecord(module = "客户档案", action = "新增客户档案")
    public ApiResponse<CustomerDtos.Response> create(@Valid @RequestBody CustomerDtos.SaveRequest request) {
        return ApiResponse.ok(customerAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('demo:customer:update')")
    @OperLogRecord(module = "客户档案", action = "修改客户档案")
    public ApiResponse<CustomerDtos.Response> update(@PathVariable Long id,
                                                  @Valid @RequestBody CustomerDtos.SaveRequest request) {
        return ApiResponse.ok(customerAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('demo:customer:delete')")
    @OperLogRecord(module = "客户档案", action = "删除客户档案")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerAppService.delete(id);
        return ApiResponse.ok(null);
    }
}
