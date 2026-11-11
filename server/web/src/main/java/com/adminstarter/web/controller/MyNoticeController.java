package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.application.NotificationAppService;
import com.adminstarter.system.application.dto.NoticeDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端“我的消息”：仅需登录态，不需要权限码。
 */
@RestController
@RequestMapping("/api/v1/my-notices")
@RequiredArgsConstructor
public class MyNoticeController {

    private final NotificationAppService notificationAppService;

    @GetMapping
    public ApiResponse<PageResponse<NoticeDtos.InboxResponse>> inbox(PageQuery pageQuery,
                                                                     @RequestParam(required = false) String readStatus) {
        return ApiResponse.ok(notificationAppService.inbox(currentUserId(), pageQuery, readStatus));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NoticeDtos.UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notificationAppService.unreadCount(currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeDtos.InboxDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(notificationAppService.inboxDetail(id, currentUserId()));
    }

    @PostMapping("/read-all")
    public ApiResponse<NoticeDtos.ReadAllResponse> readAll() {
        return ApiResponse.ok(notificationAppService.readAll(currentUserId()));
    }

    private Long currentUserId() {
        CurrentUser user = DataScopeContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return user.getUserId();
    }
}
