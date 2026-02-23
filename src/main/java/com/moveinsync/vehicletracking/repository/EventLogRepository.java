package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for EventLog entity
 */
@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findByTripId(Long tripId);
}
