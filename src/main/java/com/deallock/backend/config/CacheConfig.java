package com.deallock.backend.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig extends CachingConfigurerSupport {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Why we customize cache TTLs:
     * - Deal lists change often, so we keep them short-lived to avoid stale admin/user UIs.
     * - Still helps reduce DB load when users refresh / dashboards poll.
     */
    @Bean
    @Profile("redis")
    public CacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("userDeals", defaultConfig.entryTtl(Duration.ofSeconds(15)))
                .withCacheConfiguration("adminDeals", defaultConfig.entryTtl(Duration.ofSeconds(10)))
                .build();
    }

    /**
     * Caches are a performance optimization, not a correctness dependency.
     *
     * If Redis is down/misconfigured, Spring's default cache error handler rethrows, which turns
     * otherwise-valid endpoints (like GET /api/admin/deals) into 500s.
     *
     * We "fail open": log and ignore cache errors, and still execute the underlying method.
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed (cache={}, key={}). Continuing without cache.", cacheName(cache), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed (cache={}, key={}). Continuing without cache.", cacheName(cache), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed (cache={}, key={}). Continuing without cache.", cacheName(cache), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed (cache={}). Continuing without cache.", cacheName(cache), exception);
            }

            private String cacheName(Cache cache) {
                return cache == null ? "unknown" : cache.getName();
            }
        };
    }
}
