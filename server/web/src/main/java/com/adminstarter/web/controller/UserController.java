package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.UserAppService;
import com.adminstarter.system.application.dto.UserDtos;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAppService userAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<PageResponse<UserDtos.Response>> page(PageQuery pageQuery,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Boolean isEnabled,
                                                             @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(userAppService.page(pageQuery, keyword, isEnabled, deptId));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('system:user:list', 'system:role:list')")
    public ApiResponse<List<UserDtos.Option>> options(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userAppService.options(keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<UserDtos.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(userAppService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    @OperLogRecord(module = "用户管理", action = "新增用户")
    public ApiResponse<UserDtos.Response> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return ApiResponse.ok(userAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @OperLogRecord(module = "用户管理", action = "修改用户")
    public ApiResponse<UserDtos.Response> update(@PathVariable Long id,
                                                 @Valid @RequestBody UserDtos.UpdateRequest request) {
        return ApiResponse.ok(userAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @OperLogRecord(module = "用户管理", action = "删除用户")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userAppService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('system:user:reset')")
    @OperLogRecord(module = "用户管理", action = "重置密码")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody UserDtos.ResetPasswordRequest request) {
        userAppService.resetPassword(id, request.getPassword());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:status')")
    @OperLogRecord(module = "用户管理", action = "启用停用用户")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UserDtos.StatusRequest request) {
        userAppService.updateStatus(id, request.getIsEnabled());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign')")
    @OperLogRecord(module = "用户管理", action = "分配角色")
    public ApiResponse<Void> assignRoles(@PathVariable Long id,
                                         @Valid @RequestBody UserDtos.AssignRolesRequest request) {
        userAppService.assignRoles(id, request.getRoleIds());
        return ApiResponse.ok();
    }
}
