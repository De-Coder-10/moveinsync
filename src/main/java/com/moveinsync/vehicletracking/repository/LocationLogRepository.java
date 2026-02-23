package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.LocationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for LocationLog entity
 */
@Repository
public interface LocationLogRepository extends JpaRepository<LocationLog, Long> {
}
