package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.PickupPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for PickupPoint entity
 */
@Repository
public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {
    
    /**
     * Find pickup point by trip ID
     */
    Optional<PickupPoint> findByTripId(Long tripId);
}
