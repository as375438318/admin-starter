package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.RoleAppService;
import com.adminstarter.system.application.dto.RoleDtos;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleAppService roleAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<PageResponse<RoleDtos.Response>> page(PageQuery pageQuery,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(roleAppService.page(pageQuery, keyword, isEnabled));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('system:role:list', 'system:user:list')")
    public ApiResponse<List<RoleDtos.Response>> listAll() {
        return ApiResponse.ok(roleAppService.listAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<RoleDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(roleAppService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    @OperLogRecord(module = "角色管理", action = "新增角色")
    public ApiResponse<RoleDtos.Response> create(@Valid @RequestBody RoleDtos.SaveRequest request) {
        return ApiResponse.ok(roleAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    @OperLogRecord(module = "角色管理", action = "修改角色")
    public ApiResponse<RoleDtos.Response> update(@PathVariable Long id,
                                                 @Valid @RequestBody RoleDtos.SaveRequest request) {
        return ApiResponse.ok(roleAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperLogRecord(module = "角色管理", action = "删除角色")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleAppService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('system:role:menu')")
    @OperLogRecord(module = "角色管理", action = "分配菜单权限")
    public ApiResponse<Void> assignMenus(@PathVariable Long id,
                                         @RequestBody RoleDtos.AssignMenuRequest request) {
        roleAppService.assignMenus(id, request.getMenuIds());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/data-scope")
    @PreAuthorize("hasAuthority('system:role:scope')")
    @OperLogRecord(module = "角色管理", action = "配置数据范围")
    public ApiResponse<Void> assignDataScope(@PathVariable Long id,
                                             @Valid @RequestBody RoleDtos.DataScopeRequest request) {
        roleAppService.assignDataScope(id, request);
        return ApiResponse.ok();
    }
}
