package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.cache.CacheNamespace;
import com.adminstarter.system.application.CacheAdminAppService;
import com.adminstarter.system.application.dto.CacheDtos;
import com.adminstarter.web.log.CacheAuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端缓存管理。只按白名单命名空间操作；权限码只授 SUPER_ADMIN；每次清理写操作日志。
 */
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheAdminAppService cacheAdminAppService;
    private final CacheAuditRecorder cacheAuditRecorder;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('system:cache:list')")
    public ApiResponse<CacheDtos.OverviewResponse> overview() {
        return ApiResponse.ok(cacheAdminAppService.overview());
    }

    @GetMapping("/namespaces")
    @PreAuthorize("hasAuthority('system:cache:list')")
    public ApiResponse<?> namespaces() {
        return ApiResponse.ok(cacheAdminAppService.namespaces());
    }

    @GetMapping("/keys")
    @PreAuthorize("hasAuthority('system:cache:list')")
    public ApiResponse<CacheDtos.KeyPageResponse> keys(PageQuery pageQuery,
                                                       @RequestParam String namespace,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(cacheAdminAppService.keys(namespace, pageQuery, keyword));
    }

    @DeleteMapping("/keys")
    @PreAuthorize("hasAuthority('system:cache:clear')")
    public ApiResponse<CacheDtos.AffectResponse> deleteKey(@RequestParam String namespace,
                                                           @RequestParam String key) {
        long start = System.currentTimeMillis();
        long deleted = cacheAdminAppService.deleteKey(namespace, key);
        CacheNamespace target = cacheAdminAppService.namespace(namespace);
        cacheAuditRecorder.record("删除缓存 key", target, key, deleted, System.currentTimeMillis() - start);
        return ApiResponse.ok(new CacheDtos.AffectResponse(deleted));
    }

    @DeleteMapping("/namespaces/{code}")
    @PreAuthorize("hasAuthority('system:cache:clear')")
    public ApiResponse<CacheDtos.AffectResponse> clearNamespace(@PathVariable String code) {
        long start = System.currentTimeMillis();
        long deleted = cacheAdminAppService.clearNamespace(code);
        CacheNamespace target = cacheAdminAppService.namespace(code);
        cacheAuditRecorder.record("清空缓存命名空间", target, null, deleted, System.currentTimeMillis() - start);
        return ApiResponse.ok(new CacheDtos.AffectResponse(deleted));
    }

    @DeleteMapping("/session-cache")
    @PreAuthorize("hasAuthority('system:cache:clear')")
    public ApiResponse<CacheDtos.AffectResponse> cleanSessionCache() {
        long start = System.currentTimeMillis();
        long deleted = cacheAdminAppService.cleanSessionCache();
        CacheNamespace target = cacheAdminAppService.namespace(com.adminstarter.common.cache.CacheNamespaces.CODE_SESSION_TOUCH);
        cacheAuditRecorder.record("清理过期会话缓存", target, null, deleted, System.currentTimeMillis() - start);
        return ApiResponse.ok(new CacheDtos.AffectResponse(deleted));
    }

    @GetMapping("/clean-logs")
    @PreAuthorize("hasAuthority('system:cache:list')")
    public ApiResponse<PageResponse<CacheDtos.CleanLogResponse>> cleanLogs(PageQuery pageQuery) {
        return ApiResponse.ok(cacheAdminAppService.cleanLogs(pageQuery));
    }
}
