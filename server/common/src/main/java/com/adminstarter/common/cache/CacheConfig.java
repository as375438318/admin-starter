package com.adminstarter.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 缓存装配：默认内存实现；app.cache.type=redis 时切换为 Redis。
 */
@Configuration
@EnableScheduling
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "app.cache.type", havingValue = "memory", matchIfMissing = true)
    public CacheService inMemoryCacheService(ObjectMapper objectMapper) {
        return new InMemoryCacheService(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService redisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisCacheService(redisTemplate, objectMapper);
    }
}
