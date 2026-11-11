package com.adminstarter.system.domain.model;

/**
 * 缓存 key 的只读信息：名称、TTL 秒、类型。不读取 value。
 */
public record CacheKeyInfo(String name, long ttlSeconds, String type) {
}
