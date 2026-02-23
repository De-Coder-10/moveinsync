package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.LocationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for LocationLog entity
 */
@Repository
public interface LocationLogRepository extends JpaRepository<LocationLog, Long> {

    List<LocationLog> findByTripIdOrderByTimestampAsc(Long tripId);

    // Used to get the previous ping for incremental distance calculation
    Optional<LocationLog> findTopByTripIdOrderByTimestampDesc(Long tripId);
}
