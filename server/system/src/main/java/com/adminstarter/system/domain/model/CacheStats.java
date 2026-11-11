package com.adminstarter.system.domain.model;

/**
 * Redis 运行态概览。连接失败时 {@code connected=false}，其余数值为 0。
 */
public record CacheStats(boolean connected, long usedMemoryBytes, String usedMemoryHuman,
                         long maxMemoryBytes, String maxMemoryHuman, String maxMemoryPolicy,
                         long totalKeys) {

    public static CacheStats disconnected() {
        return new CacheStats(false, 0, null, 0, null, null, 0);
    }
}
