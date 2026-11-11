package com.adminstarter.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;

/**
 * 缓存抽象：业务只依赖本接口，默认内存实现，切 Redis 只改配置。
 */
public interface CacheService {

    void put(String key, Object value, Duration ttl);

    /**
     * 不存在或已过期时返回 null。
     */
    <T> T get(String key, TypeReference<T> type);

    String getString(String key);

    void delete(String key);

    boolean exists(String key);

    /**
     * key 不存在时写入并返回 true，已存在返回 false。
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * 原子自增；key 不存在时从 0 开始。返回自增后的值。
     */
    long increment(String key, Duration ttl);
}
