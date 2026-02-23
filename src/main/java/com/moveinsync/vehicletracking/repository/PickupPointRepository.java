package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.PickupPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PickupPoint entity
 */
@Repository
public interface PickupPointRepository extends JpaRepository<PickupPoint, Long> {

    /** Single pickup for a trip (used in geofence arrival check) */
    Optional<PickupPoint> findByTripId(Long tripId);

    /** EC5 — All pickups for a trip (used in multi-stop validation before trip closure) */
    List<PickupPoint> findAllByTripId(Long tripId);
}
