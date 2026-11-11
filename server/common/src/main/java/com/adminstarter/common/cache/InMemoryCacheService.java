package com.adminstarter.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本机无 Redis 时的默认实现：带过期的进程内缓存，仅用于单实例开发环境。
 */
@RequiredArgsConstructor
public class InMemoryCacheService implements CacheService {

    private final ObjectMapper objectMapper;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, Object value, Duration ttl) {
        store.put(key, new Entry(value, expireAt(ttl)));
    }

    @Override
    public <T> T get(String key, TypeReference<T> type) {
        Entry entry = read(key);
        if (entry == null || entry.value == null) {
            return null;
        }
        return objectMapper.convertValue(entry.value, type);
    }

    @Override
    public String getString(String key) {
        Entry entry = read(key);
        if (entry == null || entry.value == null) {
            return null;
        }
        return String.valueOf(entry.value);
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return read(key) != null;
    }

    @Override
    public synchronized boolean setIfAbsent(String key, String value, Duration ttl) {
        Entry existing = read(key);
        if (existing != null) {
            return false;
        }
        store.put(key, new Entry(value, expireAt(ttl)));
        return true;
    }

    @Override
    public synchronized long increment(String key, Duration ttl) {
        Entry existing = read(key);
        long next = existing == null ? 1L : ((AtomicLong) existing.value).incrementAndGet();
        if (existing == null) {
            store.put(key, new Entry(new AtomicLong(1L), expireAt(ttl)));
        } else {
            existing.expireAt = expireAt(ttl);
        }
        return next;
    }

    @Scheduled(fixedDelay = 60000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt <= now);
    }

    private Entry read(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt <= System.currentTimeMillis()) {
            store.remove(key);
            return null;
        }
        return entry;
    }

    private long expireAt(Duration ttl) {
        return System.currentTimeMillis() + ttl.toMillis();
    }

    private static final class Entry {
        private final Object value;
        private long expireAt;

        private Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
