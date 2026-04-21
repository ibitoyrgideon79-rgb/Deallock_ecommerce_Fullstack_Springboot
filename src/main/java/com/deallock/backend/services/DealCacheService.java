package com.deallock.backend.services;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class DealCacheService {

    private final CacheManager cacheManager;

    public DealCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictUserDeals(String email) {
        Cache cache = cacheManager.getCache("userDeals");
        if (cache != null && email != null && !email.isBlank()) {
            cache.evict(email);
        }
    }

    public void evictAdminDeals() {
        Cache cache = cacheManager.getCache("adminDeals");
        if (cache != null) {
            cache.evict("all");
        }
    }
}

