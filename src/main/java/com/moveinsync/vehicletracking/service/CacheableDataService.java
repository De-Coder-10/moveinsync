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
 * Wraps static-data repository calls with Spring Cache annotations.
 * Kept as a separate bean so self-invocation doesn't bypass the AOP proxy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheableDataService {

    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final VehicleRepository        vehicleRepository;
    private final DriverRepository         driverRepository;

    /** Returns all office geofences, cached to avoid repeated DB reads on every dashboard poll. */
    @Cacheable(CacheConfig.CACHE_OFFICE_GEOFENCES)
    public List<OfficeGeofence> getOfficeGeofences() {
        log.debug("[CACHE MISS] officeGeofences — loading from DB");
        return officeGeofenceRepository.findAll();
    }

    /** Returns all vehicles (static for the lifetime of a simulation session). */
    @Cacheable(CacheConfig.CACHE_VEHICLE_DRIVER)
    public List<Vehicle> getVehicles() {
        log.debug("[CACHE MISS] vehicles — loading from DB");
        return vehicleRepository.findAll();
    }

    /** Returns the driver assigned to a vehicle, cached per vehicleId. */
    @Cacheable(value = CacheConfig.CACHE_VEHICLE_DRIVER, key = "'driver-' + #vehicleId")
    public Optional<Driver> getDriverByVehicleId(Long vehicleId) {
        log.debug("[CACHE MISS] driver for vehicle #{} — loading from DB", vehicleId);
        return driverRepository.findByVehicleId(vehicleId);
    }

    /** Evicts all static cache entries; called on simulation reset so the next poll reloads from DB. */
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_OFFICE_GEOFENCES, allEntries = true),
            @CacheEvict(value = CacheConfig.CACHE_VEHICLE_DRIVER,   allEntries = true)
    })
    public void evictStaticCaches() {
        log.info("[CACHE EVICT] Static caches cleared — next poll will reload from DB");
    }
}
