package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.entity.EventLog;
import com.moveinsync.vehicletracking.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AuditService — Audit & Compliance Tracking
 *
 * Provides read-only query methods for the audit event log.
 * Used by AuditController to expose compliance data via REST.
 *
 * Use cases:
 *  - Client reporting   : "Show all events for trip #42"
 *  - SLA validation     : "Was PICKUP_ARRIVED within the SLA window?"
 *  - Dispute resolution : "Employee claims cab never arrived — query PICKUP_ARRIVED record"
 *  - Billing reports    : "All completed trips between 06:00 and 10:00"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final EventLogRepository eventLogRepository;

    /**
     * Returns all audit events for a specific trip, ordered chronologically.
     *
     * Useful for full trip audit trail:
     *   PICKUP_ARRIVED → OFFICE_REACHED → TRIP_COMPLETED
     *
     * @param tripId the trip to query
     * @return list of EventLog records, oldest first
     */
    @Transactional(readOnly = true)
    public List<EventLog> getEventsByTripId(Long tripId) {
        log.debug("AUDIT: Querying events for trip #{}", tripId);
        List<EventLog> events = eventLogRepository.findByTripIdOrderByTimestampAsc(tripId);
        log.info("AUDIT: Found {} event(s) for trip #{}", events.size(), tripId);
        return events;
    }

    /**
     * Returns all audit events for a specific vehicle, ordered newest-first.
     *
     * Useful for vehicle-level reporting:
     *   "How many trips did vehicle KA01AB1234 complete this week?"
     *
     * @param vehicleId the vehicle to query
     * @return list of EventLog records, newest first
     */
    @Transactional(readOnly = true)
    public List<EventLog> getEventsByVehicleId(Long vehicleId) {
        log.debug("AUDIT: Querying events for vehicle #{}", vehicleId);
        List<EventLog> events = eventLogRepository.findByVehicleIdOrderByTimestampDesc(vehicleId);
        log.info("AUDIT: Found {} event(s) for vehicle #{}", events.size(), vehicleId);
        return events;
    }

    /**
     * Returns all audit events within a server-timestamp range, ordered chronologically.
     *
     * Useful for SLA reports and client billing:
     *   "All geofence events between 06:00 AM and 10:00 AM today"
     *
     * @param start inclusive start of the time window
     * @param end   inclusive end of the time window
     * @return list of EventLog records within the range, oldest first
     * @throws IllegalArgumentException if start is after end
     */
    @Transactional(readOnly = true)
    public List<EventLog> getEventsByTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "start (" + start + ") must not be after end (" + end + ")");
        }
        log.debug("AUDIT: Querying events between {} and {}", start, end);
        List<EventLog> events = eventLogRepository.findByTimestampBetweenOrderByTimestampAsc(start, end);
        log.info("AUDIT: Found {} event(s) between {} and {}", events.size(), start, end);
        return events;
    }
}
