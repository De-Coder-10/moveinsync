package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.OfficeGeofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



/**
 * Repository interface for OfficeGeofence entity
 */
@Repository
public interface OfficeGeofenceRepository extends JpaRepository<OfficeGeofence, Long> {
}
