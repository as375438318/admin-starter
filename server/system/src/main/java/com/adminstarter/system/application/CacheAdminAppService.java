package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.cache.CacheNamespace;
import com.adminstarter.common.cache.CacheNamespaces;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.application.dto.CacheDtos;
import com.adminstarter.system.application.dto.LogDtos;
import com.adminstarter.system.domain.model.CacheKeyInfo;
import com.adminstarter.system.domain.model.CacheKeyPage;
import com.adminstarter.system.domain.model.CacheStats;
import com.adminstarter.system.domain.repository.CacheAdminRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存管理应用服务：只按白名单命名空间操作，不读取 value，清理动作由控制层写审计。
 */
@Service
@RequiredArgsConstructor
public class CacheAdminAppService {

    private static final String AUDIT_MODULE = "缓存管理";
    private static final int MAX_KEYWORD_LENGTH = 128;

    private final CacheAdminRepository cacheAdminRepository;
    private final OperLogAppService operLogAppService;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.type:memory}")
    private String cacheType;

    public CacheDtos.OverviewResponse overview() {
        CacheStats stats = cacheAdminRepository.stats();
        List<CacheDtos.NamespaceCount> namespaces = new ArrayList<>();
        long registered = 0;
        for (CacheNamespace namespace : CacheNamespaces.all()) {
            long count = stats.connected() ? safeCount(namespace) : 0;
            if (count > 0) {
                registered += count;
            }
            namespaces.add(new CacheDtos.NamespaceCount(namespace.code(), namespace.name(),
                    namespace.prefix(), count));
        }
        long unregistered = stats.connected() ? Math.max(0, stats.totalKeys() - registered) : 0;
        return new CacheDtos.OverviewResponse(stats.connected(), cacheType,
                stats.usedMemoryBytes(), stats.usedMemoryHuman(),
                stats.maxMemoryBytes(), stats.maxMemoryHuman(), stats.maxMemoryPolicy(),
                stats.totalKeys(), unregistered, namespaces);
    }

    public List<CacheDtos.NamespaceResponse> namespaces() {
        List<CacheDtos.NamespaceResponse> list = new ArrayList<>();
        for (CacheNamespace namespace : CacheNamespaces.all()) {
            list.add(toNamespaceResponse(namespace, safeCount(namespace)));
        }
        return list;
    }

    public CacheDtos.KeyPageResponse keys(String code, PageQuery pageQuery, String keyword) {
        CacheNamespace namespace = namespace(code);
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "keyword 不能超过 " + MAX_KEYWORD_LENGTH + " 个字符");
        }
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        CacheKeyPage result = cacheAdminRepository.scan(namespace, page, pageSize, keyword);
        List<CacheDtos.KeyResponse> list = result.list().stream()
                .map(key -> new CacheDtos.KeyResponse(key.name(), key.ttlSeconds(), key.type()))
                .toList();
        return new CacheDtos.KeyPageResponse(list, result.total(), page, pageSize, result.truncated(),
                toNamespaceResponse(namespace, safeCount(namespace)));
    }

    public long deleteKey(String code, String key) {
        CacheNamespace namespace = namespace(code);
        requireClearable(namespace);
        if (!namespace.matches(key)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "key 不属于命名空间【" + namespace.name() + "】");
        }
        return cacheAdminRepository.deleteKey(namespace, key);
    }

    public long clearNamespace(String code) {
        CacheNamespace namespace = namespace(code);
        requireClearable(namespace);
        return cacheAdminRepository.clearNamespace(namespace);
    }

    public long cleanSessionCache() {
        CacheNamespace namespace = namespace(CacheNamespaces.CODE_SESSION_TOUCH);
        return cacheAdminRepository.clearNamespace(namespace);
    }

    public CacheNamespace namespace(String code) {
        return CacheNamespaces.findByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "未登记的缓存命名空间: " + code));
    }

    public PageResponse<CacheDtos.CleanLogResponse> cleanLogs(PageQuery pageQuery) {
        PageResponse<LogDtos.OperLogResponse> page = operLogAppService.page(pageQuery, AUDIT_MODULE, null, null, null);
        List<CacheDtos.CleanLogResponse> list = page.getList().stream().map(this::toCleanLog).toList();
        return PageResponse.of(page.getPage(), page.getPageSize(), list, page.getTotal());
    }

    private CacheDtos.CleanLogResponse toCleanLog(LogDtos.OperLogResponse log) {
        CacheDtos.CleanLogResponse response = new CacheDtos.CleanLogResponse();
        response.setId(log.getId());
        response.setAction(log.getAction());
        response.setUsername(log.getUsername());
        response.setIp(log.getIp());
        response.setCreatedAt(log.getCreatedAt());
        JsonNode params = readParams(log.getParams());
        if (params != null) {
            response.setNamespaceCode(text(params, "namespaceCode"));
            response.setNamespacePrefix(text(params, "namespacePrefix"));
            response.setKeyName(text(params, "key"));
            JsonNode deleted = params.get("deletedCount");
            response.setDeletedCount(deleted == null ? 0 : deleted.asInt());
        }
        return response;
    }

    private JsonNode readParams(String params) {
        if (params == null || params.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(params);
        } catch (Exception exception) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private CacheDtos.NamespaceResponse toNamespaceResponse(CacheNamespace namespace, long keyCount) {
        return new CacheDtos.NamespaceResponse(namespace.code(), namespace.name(), namespace.prefix(),
                namespace.mode().name(), namespace.purpose(), namespace.clearEffect(),
                namespace.sensitive(), namespace.clearable(), keyCount);
    }

    private void requireClearable(CacheNamespace namespace) {
        if (!namespace.clearable()) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "命名空间【" + namespace.name() + "】不允许清理：" + namespace.clearEffect());
        }
    }

    private long safeCount(CacheNamespace namespace) {
        try {
            return cacheAdminRepository.count(namespace);
        } catch (BizException exception) {
            return -1;
        }
    }
}
