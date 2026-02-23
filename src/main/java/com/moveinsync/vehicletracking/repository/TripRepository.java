package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Trip entity
 */
@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
}
