package com.adminstarter.system.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 缓存管理接口响应体。只包含 key 名、TTL 与类型，不包含任何 value。
 */
public final class CacheDtos {

    private CacheDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamespaceCount {
        private String code;
        private String name;
        private String prefix;
        private long keyCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewResponse {
        private boolean connected;
        private String cacheType;
        private long usedMemoryBytes;
        private String usedMemoryHuman;
        private long maxMemoryBytes;
        private String maxMemoryHuman;
        private String maxMemoryPolicy;
        private long totalKeys;
        private long unregisteredKeys;
        private List<NamespaceCount> namespaces;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamespaceResponse {
        private String code;
        private String name;
        private String prefix;
        private String mode;
        private String purpose;
        private String clearEffect;
        private boolean sensitive;
        private boolean clearable;
        private long keyCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyResponse {
        private String name;
        private long ttlSeconds;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyPageResponse {
        private List<KeyResponse> list;
        private long total;
        private int page;
        private int pageSize;
        private boolean truncated;
        private NamespaceResponse namespace;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AffectResponse {
        private long deleted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CleanLogResponse {
        private Long id;
        private String action;
        private String namespaceCode;
        private String namespacePrefix;
        private String keyName;
        private int deletedCount;
        private String username;
        private String ip;
        private Instant createdAt;
    }
}
