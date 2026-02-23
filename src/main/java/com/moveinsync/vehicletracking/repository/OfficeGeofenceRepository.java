package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.OfficeGeofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for OfficeGeofence entity
 */
@Repository
public interface OfficeGeofenceRepository extends JpaRepository<OfficeGeofence, Long> {

    /** Lookup a geofence by its human-readable name (case-sensitive) */
    Optional<OfficeGeofence> findByName(String name);

    /** Find all geofences of a given type ("CIRCULAR" or "POLYGON") */
    List<OfficeGeofence> findByGeofenceType(String geofenceType);
}
