package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.system.application.DeptAppService;
import com.adminstarter.system.application.dto.DeptDtos;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptAppService deptAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:dept:list')")
    public ApiResponse<List<DeptDtos.Response>> tree() {
        return ApiResponse.ok(deptAppService.tree());
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('system:dept:list', 'system:user:list', 'system:role:list')")
    public ApiResponse<List<DeptDtos.Option>> options() {
        return ApiResponse.ok(deptAppService.options());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:create')")
    @OperLogRecord(module = "部门管理", action = "新增部门")
    public ApiResponse<DeptDtos.Response> create(@Valid @RequestBody DeptDtos.SaveRequest request) {
        return ApiResponse.ok(deptAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:update')")
    @OperLogRecord(module = "部门管理", action = "修改部门")
    public ApiResponse<DeptDtos.Response> update(@PathVariable Long id,
                                                @Valid @RequestBody DeptDtos.SaveRequest request) {
        return ApiResponse.ok(deptAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:delete')")
    @OperLogRecord(module = "部门管理", action = "删除部门")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deptAppService.delete(id);
        return ApiResponse.ok();
    }
}
