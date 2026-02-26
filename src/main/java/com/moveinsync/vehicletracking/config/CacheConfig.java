package com.moveinsync.vehicletracking.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caching configuration using Caffeine (high-performance, in-process cache).
 *
 * Two caches defined:
 *
 *   officeGeofences  — Geofence boundaries never change at runtime.
 *                      Dashboard polls /api/dashboard/data every 2 seconds →
 *                      without cache that's 30 identical DB reads/minute for static data.
 *                      TTL: 60 minutes. Max entries: 20.
 *
 *   vehicleDriverData — Vehicle registrations + per-vehicle driver records are static.
 *                       Cached per vehicleId key to avoid N DB calls per dashboard poll.
 *                       TTL: 60 minutes. Max entries: 50.
 *
 * Cache Eviction Policy (belt-and-suspenders):
 *   1. TTL-based  → expireAfterWrite ensures no stale entry lives beyond 60 min.
 *   2. Explicit   → CacheableDataService.evictStaticCaches() clears both caches
 *                   on every simulation reset (forced fresh load).
 *   3. LRU        → maximumSize caps memory; Caffeine evicts least-recently-used
 *                   entries when limit is approached.
 *
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

//      Cache name for office geofence boundaries (static data) 
    public static final String CACHE_OFFICE_GEOFENCES = "officeGeofences";

//      Cache name for vehicle list and per-vehicle driver records (static data) 
    public static final String CACHE_VEHICLE_DRIVER = "vehicleDriverData";

    @Bean
    public CacheManager cacheManager() {
        log.info("[CACHE] Initialising Caffeine CacheManager — caches: '{}', '{}'",
                CACHE_OFFICE_GEOFENCES, CACHE_VEHICLE_DRIVER);

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache(CACHE_OFFICE_GEOFENCES, 60, 20),   // tiny table, 60-min TTL
                buildCache(CACHE_VEHICLE_DRIVER,   60, 50)    // fleet-sized, 60-min TTL
        ));
        return manager;
    }


//       Builds a named Caffeine cache
    private CaffeineCache buildCache(String name, int ttlMinutes, int maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                        .maximumSize(maxSize)
                        .recordStats()
                        .build());
    }
}
