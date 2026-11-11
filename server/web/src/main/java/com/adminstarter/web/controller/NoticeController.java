package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.NotificationAppService;
import com.adminstarter.system.application.dto.NoticeDtos;
import com.adminstarter.web.log.OperLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NotificationAppService notificationAppService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:notice:list')")
    public ApiResponse<PageResponse<NoticeDtos.Response>> page(PageQuery pageQuery,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String level,
                                                               @RequestParam(required = false) String type,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) Long deptId) {
        return ApiResponse.ok(notificationAppService.page(pageQuery, keyword, level, type, status, deptId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:notice:create')")
    @OperLogRecord(module = "通知公告", action = "发布通知")
    public ApiResponse<NoticeDtos.Response> publish(@Valid @RequestBody NoticeDtos.PublishRequest request) {
        return ApiResponse.ok(notificationAppService.publishAsCurrentUser(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:list')")
    public ApiResponse<NoticeDtos.Response> detail(@PathVariable Long id) {
        return ApiResponse.ok(notificationAppService.getById(id));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('system:notice:revoke')")
    @OperLogRecord(module = "通知公告", action = "撤回通知")
    public ApiResponse<NoticeDtos.Response> revoke(@PathVariable Long id) {
        return ApiResponse.ok(notificationAppService.revoke(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:notice:delete')")
    @OperLogRecord(module = "通知公告", action = "删除通知")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notificationAppService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/readers")
    @PreAuthorize("hasAuthority('system:notice:read-stats')")
    public ApiResponse<PageResponse<NoticeDtos.ReaderResponse>> readers(@PathVariable Long id,
                                                                        PageQuery pageQuery,
                                                                        @RequestParam(required = false) String readStatus,
                                                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(notificationAppService.readers(id, pageQuery, readStatus, keyword));
    }
}
