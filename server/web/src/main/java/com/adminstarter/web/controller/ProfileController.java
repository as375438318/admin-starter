package com.adminstarter.web.controller;

import com.adminstarter.auth.application.AuthAppService;
import com.adminstarter.auth.application.dto.AuthTokens;
import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.ProfileAppService;
import com.adminstarter.system.application.dto.LogDtos;
import com.adminstarter.system.application.dto.ProfileDtos;
import com.adminstarter.web.auth.AuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileAppService profileAppService;
    private final AuthAppService authAppService;
    private final AuthCookieService cookieService;

    @GetMapping
    public ApiResponse<ProfileDtos.Response> profile() {
        return ApiResponse.ok(profileAppService.profile());
    }

    @PutMapping
    public ApiResponse<ProfileDtos.Response> update(@Valid @RequestBody ProfileDtos.UpdateRequest request) {
        return ApiResponse.ok(profileAppService.updateProfile(request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ProfileDtos.ChangePasswordRequest request,
                                            HttpServletResponse response) {
        Long userId = profileAppService.changePassword(request);
        // 本人改密：版本已自增，其他设备令牌立即失效；当前设备用新版本重签，避免把自己踢下线。
        CurrentUser current = DataScopeContext.get();
        AuthTokens tokens = authAppService.reissueAfterPasswordChange(userId,
                current == null ? null : current.getSessionId());
        cookieService.writeTokens(response, tokens);
        return ApiResponse.ok();
    }

    @GetMapping("/login-logs")
    public ApiResponse<PageResponse<LogDtos.LoginLogResponse>> loginLogs(PageQuery pageQuery) {
        return ApiResponse.ok(profileAppService.loginLogs(pageQuery));
    }
}
