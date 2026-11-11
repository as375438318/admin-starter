package com.adminstarter.system.domain.repository;

import com.adminstarter.common.cache.CacheNamespace;
import com.adminstarter.system.domain.model.CacheKeyPage;
import com.adminstarter.system.domain.model.CacheStats;

/**
 * 缓存管理的数据访问入口。实现必须使用 SCAN 游标，禁止 KEYS，且只做名称/TTL/类型读取。
 */
public interface CacheAdminRepository {

    CacheStats stats();

    long count(CacheNamespace namespace);

    CacheKeyPage scan(CacheNamespace namespace, int page, int pageSize, String keyword);

    long deleteKey(CacheNamespace namespace, String key);

    long clearNamespace(CacheNamespace namespace);
}
