package com.deallock.backend.config;

import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
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
public class CacheConfig {

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
}
