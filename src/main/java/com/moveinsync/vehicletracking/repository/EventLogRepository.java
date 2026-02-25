package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.EventLog;
import com.moveinsync.vehicletracking.entity.GeofenceEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for EventLog — supports all audit & compliance queries.
 *
 * Uses Spring Data JPA derived queries; the DB indexes on
 * (trip_id), (vehicle_id), and (trip_id, event_type) ensure these
 * queries are fast even at large scale.
 */
@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    // ── Trip-scoped queries ────────────────────────────────────────────────────

    /** Returns all events for a trip ordered oldest-first (chronological audit trail). */
    List<EventLog> findByTripIdOrderByTimestampAsc(Long tripId);

    /** Alias kept for compatibility with DashboardApiController reset logic. */
    default List<EventLog> findByTripId(Long tripId) {
        return findByTripIdOrderByTimestampAsc(tripId);
    }

    // ── Idempotency guard ──────────────────────────────────────────────────────

    /**
     * EC6 — Duplicate Event Protection.
     * Returns true if an event of the given type already exists for this trip.
     * Uses the composite index (trip_id, event_type) for O(log n) lookup.
     *
     * @param tripId    the trip to check
     * @param eventType the typed enum — prevents string typos
     */
    boolean existsByTripIdAndEventType(Long tripId, GeofenceEventType eventType);
}
