package com.adminstarter.system.infrastructure.cache;

import com.adminstarter.common.cache.CacheNamespace;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.domain.model.CacheKeyInfo;
import com.adminstarter.system.domain.model.CacheKeyPage;
import com.adminstarter.system.domain.model.CacheStats;
import com.adminstarter.system.domain.repository.CacheAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存管理实现。全量遍历一律 SCAN 游标；只读取 key 名、TTL 与类型，不读取 value。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisCacheAdminRepository implements CacheAdminRepository {

    private static final int SCAN_BATCH = 500;
    private static final int MAX_SCAN_BATCHES = 256;

    private final StringRedisTemplate redisTemplate;

    @Override
    public CacheStats stats() {
        try {
            CacheStats stats = redisTemplate.execute((RedisCallback<CacheStats>) connection -> {
                connection.ping();
                long totalKeys = connection.serverCommands().dbSize() == null
                        ? 0 : connection.serverCommands().dbSize();
                Properties info = connection.serverCommands().info("memory");
                return new CacheStats(true,
                        longValue(info, "used_memory"),
                        textValue(info, "used_memory_human"),
                        longValue(info, "maxmemory"),
                        textValue(info, "maxmemory_human"),
                        textValue(info, "maxmemory_policy"),
                        totalKeys);
            });
            return stats == null ? CacheStats.disconnected() : stats;
        } catch (RuntimeException exception) {
            log.warn("读取 Redis 概览失败: {}", exception.getMessage());
            return CacheStats.disconnected();
        }
    }

    @Override
    public long count(CacheNamespace namespace) {
        if (namespace.mode() == CacheNamespace.Mode.EXACT) {
            return Boolean.TRUE.equals(redisTemplate.hasKey(namespace.prefix())) ? 1 : 0;
        }
        long count = 0;
        int batches = 0;
        try (Cursor<String> cursor = scan(namespace)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
                if (count % SCAN_BATCH == 0 && ++batches >= MAX_SCAN_BATCHES) {
                    log.warn("命名空间 {} 超过最大扫描批次，计数可能偏小", namespace.code());
                    break;
                }
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        return count;
    }

    @Override
    public CacheKeyPage scan(CacheNamespace namespace, int page, int pageSize, String keyword) {
        if (namespace.mode() == CacheNamespace.Mode.EXACT) {
            return scanExact(namespace, page, keyword);
        }
        long offset = (long) (page - 1) * pageSize;
        long total = 0;
        boolean truncated = false;
        int batches = 0;
        int inBatch = 0;
        List<CacheKeyInfo> list = new ArrayList<>(pageSize);
        try (Cursor<String> cursor = scan(namespace)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (++inBatch >= SCAN_BATCH) {
                    inBatch = 0;
                    if (++batches >= MAX_SCAN_BATCHES) {
                        truncated = true;
                        break;
                    }
                }
                if (keyword != null && !keyword.isBlank() && !key.contains(keyword)) {
                    continue;
                }
                if (total >= offset && list.size() < pageSize) {
                    list.add(keyInfo(key));
                }
                total++;
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        return new CacheKeyPage(list, total, truncated);
    }

    @Override
    public long deleteKey(CacheNamespace namespace, String key) {
        try {
            Boolean removed = redisTemplate.delete(key);
            return Boolean.TRUE.equals(removed) ? 1 : 0;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public long clearNamespace(CacheNamespace namespace) {
        if (namespace.mode() == CacheNamespace.Mode.EXACT) {
            return deleteKey(namespace, namespace.prefix());
        }
        long deleted = 0;
        List<String> batch = new ArrayList<>(SCAN_BATCH);
        try (Cursor<String> cursor = scan(namespace)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH) {
                    deleted += remove(batch);
                    batch.clear();
                }
            }
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        if (!batch.isEmpty()) {
            deleted += remove(batch);
        }
        return deleted;
    }

    private CacheKeyPage scanExact(CacheNamespace namespace, int page, String keyword) {
        String key = namespace.prefix();
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return new CacheKeyPage(List.of(), 0, false);
        }
        if (keyword != null && !keyword.isBlank() && !key.contains(keyword)) {
            return new CacheKeyPage(List.of(), 0, false);
        }
        if (page > 1) {
            return new CacheKeyPage(List.of(), 1, false);
        }
        return new CacheKeyPage(List.of(keyInfo(key)), 1, false);
    }

    private CacheKeyInfo keyInfo(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        DataType type = redisTemplate.type(key);
        return new CacheKeyInfo(key, ttl == null ? -2 : ttl, type == null ? "none" : type.code());
    }

    private long remove(List<String> keys) {
        Long removed = redisTemplate.delete(keys);
        return removed == null ? 0 : removed;
    }

    private Cursor<String> scan(CacheNamespace namespace) {
        return redisTemplate.scan(ScanOptions.scanOptions()
                .match(namespace.prefix() + "*")
                .count(SCAN_BATCH)
                .build());
    }

    private BizException unavailable(RuntimeException exception) {
        log.warn("Redis 缓存操作失败: {}", exception.getMessage());
        return new BizException(ErrorCode.SYSTEM_ERROR, "缓存服务不可用，请稍后重试");
    }

    private static long longValue(Properties properties, String key) {
        if (properties == null) {
            return 0;
        }
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String textValue(Properties properties, String key) {
        return properties == null ? null : properties.getProperty(key);
    }
}
