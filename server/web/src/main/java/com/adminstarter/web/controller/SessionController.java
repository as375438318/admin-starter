package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.UserSessionAppService;
import com.adminstarter.system.application.dto.SessionDtos;
import com.adminstarter.web.log.OperLogRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端会话管理。权限码只授予 SUPER_ADMIN；会话表豁免数据权限，按 userId 普通筛选。
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final UserSessionAppService sessionAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:session:list')")
    public ApiResponse<PageResponse<SessionDtos.Response>> page(PageQuery pageQuery,
                                                               @RequestParam(required = false) Long userId,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Boolean online) {
        return ApiResponse.ok(sessionAppService.page(pageQuery, userId, keyword, online));
    }

    @DeleteMapping("/invalidated")
    @PreAuthorize("hasAuthority('system:session:clean')")
    @OperLogRecord(module = "会话管理", action = "清理失效会话记录")
    public ApiResponse<SessionDtos.AffectResponse> cleanInvalidated() {
        return ApiResponse.ok(new SessionDtos.AffectResponse(sessionAppService.cleanInvalidated()));
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('system:session:kick')")
    @OperLogRecord(module = "会话管理", action = "按用户强制下线")
    public ApiResponse<SessionDtos.AffectResponse> kickUser(@PathVariable Long userId) {
        return ApiResponse.ok(new SessionDtos.AffectResponse(sessionAppService.kickUser(userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:session:kick')")
    @OperLogRecord(module = "会话管理", action = "强制下线会话")
    public ApiResponse<SessionDtos.AffectResponse> kick(@PathVariable Long id) {
        CurrentUser current = DataScopeContext.get();
        String currentSessionId = current == null ? null : current.getSessionId();
        return ApiResponse.ok(new SessionDtos.AffectResponse(sessionAppService.kick(id, currentSessionId)));
    }
}
