package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.system.application.MenuAppService;
import com.adminstarter.system.application.dto.MenuDtos;
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
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuAppService menuAppService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('system:menu:list', 'system:role:list')")
    public ApiResponse<List<MenuDtos.Response>> tree() {
        return ApiResponse.ok(menuAppService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    @OperLogRecord(module = "菜单管理", action = "新增菜单")
    public ApiResponse<MenuDtos.Response> create(@Valid @RequestBody MenuDtos.SaveRequest request) {
        return ApiResponse.ok(menuAppService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    @OperLogRecord(module = "菜单管理", action = "修改菜单")
    public ApiResponse<MenuDtos.Response> update(@PathVariable Long id,
                                                @Valid @RequestBody MenuDtos.SaveRequest request) {
        return ApiResponse.ok(menuAppService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @OperLogRecord(module = "菜单管理", action = "删除菜单")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuAppService.delete(id);
        return ApiResponse.ok();
    }
}
