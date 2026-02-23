package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.config.CacheConfig;
import com.moveinsync.vehicletracking.entity.Driver;
import com.moveinsync.vehicletracking.entity.OfficeGeofence;
import com.moveinsync.vehicletracking.entity.Vehicle;
import com.moveinsync.vehicletracking.repository.DriverRepository;
import com.moveinsync.vehicletracking.repository.OfficeGeofenceRepository;
import com.moveinsync.vehicletracking.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service wrapping static-data repository calls with Spring Cache annotations.
 *
 * Why a separate service?
 *   @Cacheable is implemented via Spring AOP proxies. Self-calls within the same
 *   bean bypass the proxy, so cache annotations are silently ignored.
 *   Placing these methods here ensures every call goes through the proxy and
 *   the cache is always consulted first.
 *
 * Caching strategy:
 *   READ  → @Cacheable  : returns cached value if present;
 *                         on miss, loads from DB, stores in cache, returns.
 *   EVICT → @CacheEvict : clears cache entries immediately;
 *                         fired on simulation reset so next poll reloads from DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheableDataService {

    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final VehicleRepository        vehicleRepository;
    private final DriverRepository         driverRepository;

    // ────────────────────────────────────────────────────────────────────────
    // READ methods (cache population)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns all office geofences.
     *
     * Hit  path : O(1) — Caffeine hash lookup, zero DB round-trip.
     * Miss path : O(n) — full table scan (tiny table, immediately cached).
     *
     * Called every 2 seconds by dashboard polling; without caching this is
     * 30 identical DB reads per minute for data that never changes at runtime.
     */
    @Cacheable(CacheConfig.CACHE_OFFICE_GEOFENCES)
    public List<OfficeGeofence> getOfficeGeofences() {
        log.debug("[CACHE MISS] officeGeofences — loading from DB");
        return officeGeofenceRepository.findAll();
    }

    /**
     * Returns all vehicles.
     * Vehicle registrations are static for the lifetime of a simulation session.
     */
    @Cacheable(CacheConfig.CACHE_VEHICLE_DRIVER)
    public List<Vehicle> getVehicles() {
        log.debug("[CACHE MISS] vehicles — loading from DB");
        return vehicleRepository.findAll();
    }

    /**
     * Returns the driver assigned to a vehicle, cached per vehicleId.
     *
     * Cache key  : "driver-{vehicleId}" — avoids collision with the vehicle list entry.
     * Called N times per dashboard poll (once per active trip) — caching turns
     * N DB queries into N O(1) lookups after the first poll.
     *
     * @param vehicleId the vehicle's primary key
     */
    @Cacheable(value = CacheConfig.CACHE_VEHICLE_DRIVER, key = "'driver-' + #vehicleId")
    public Optional<Driver> getDriverByVehicleId(Long vehicleId) {
        log.debug("[CACHE MISS] driver for vehicle #{} — loading from DB", vehicleId);
        return driverRepository.findByVehicleId(vehicleId);
    }

    // ────────────────────────────────────────────────────────────────────────
    // EVICT method (cache invalidation)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Evicts all entries from both static caches.
     *
     * Called by DashboardApiController.resetTrip() so that the very next
     * dashboard poll reloads fresh data from the DB rather than serving
     * potentially stale cached entries.
     *
     * Eviction policy in effect:
     *   1. Explicit (this method)  — immediate, triggered on reset.
     *   2. TTL-based               — Caffeine evicts entries 60 min after last write.
     *   3. LRU-based               — Caffeine evicts LRU entries when maximumSize is hit.
     */
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_OFFICE_GEOFENCES, allEntries = true),
            @CacheEvict(value = CacheConfig.CACHE_VEHICLE_DRIVER,   allEntries = true)
    })
    public void evictStaticCaches() {
        log.info("[CACHE EVICT] Static caches cleared — next poll will reload from DB");
    }
}
