package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.ChangeAuditAppService;
import com.adminstarter.system.application.dto.ChangeAuditDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置面变更审计（参数 / 字典 / 菜单 / 角色授权）。
 *
 * <p>**只读**：没有创建/修改/删除接口——审计写入只能由业务事务内的 recorder 产生，
 * 不开放 HTTP 写入口，避免"把审计记录改掉"（断言 #109）。</p>
 */
@RestController
@RequestMapping("/api/v1/change-audits")
@RequiredArgsConstructor
public class ChangeAuditController {

    private final ChangeAuditAppService changeAuditAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:audit:list')")
    public ApiResponse<PageResponse<ChangeAuditDtos.Response>> page(PageQuery pageQuery,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetKey,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(changeAuditAppService.page(pageQuery, targetType, targetKey, operator, action, keyword));
    }
}