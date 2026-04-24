package com.deallock.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class DealCacheService {

    private static final Logger log = LoggerFactory.getLogger(DealCacheService.class);
    private final CacheManager cacheManager;

    public DealCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictUserDeals(String key) {
        Cache cache = cacheManager.getCache("userDeals");
        if (cache != null && key != null && !key.isBlank()) {
            try {
                cache.evict(key);
            } catch (RuntimeException ex) {
                log.warn("Cache evict failed (cache=userDeals, key={}). Continuing.", key, ex);
            }
        }
    }

    public void evictUserDealsById(Integer userId) {
        if (userId == null) return;
        Cache cache = cacheManager.getCache("userDeals");
        if (cache != null) {
            try {
                cache.evict(userId);
            } catch (RuntimeException ex) {
                log.warn("Cache evict failed (cache=userDeals, key={}). Continuing.", userId, ex);
            }
        }
    }

    public void evictAdminDeals() {
        Cache cache = cacheManager.getCache("adminDeals");
        if (cache != null) {
            try {
                cache.evict("all");
            } catch (RuntimeException ex) {
                log.warn("Cache evict failed (cache=adminDeals, key=all). Continuing.", ex);
            }
        }
    }
}
