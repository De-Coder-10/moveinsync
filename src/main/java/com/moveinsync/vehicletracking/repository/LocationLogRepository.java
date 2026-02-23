package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.LocationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for LocationLog entity
 */
@Repository
public interface LocationLogRepository extends JpaRepository<LocationLog, Long> {

    List<LocationLog> findByTripIdOrderByTimestampAsc(Long tripId);
}
