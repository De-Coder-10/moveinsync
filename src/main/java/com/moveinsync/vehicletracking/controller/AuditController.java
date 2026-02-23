package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.entity.EventLog;
import com.moveinsync.vehicletracking.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AuditController — Audit & Compliance REST API
 *
 * Exposes the geofence event audit log for:
 *  - SLA validation
 *  - Dispute resolution  (e.g., "prove cab arrived at pickup")
 *  - Client reporting
 *  - Billing reconciliation
 *
 * All timestamps in responses are server-generated (never device time).
 *
 * Endpoints:
 *  GET /api/audit/trip/{tripId}            — full audit trail for one trip
 *  GET /api/audit/vehicle/{vehicleId}      — all events for a vehicle
 *  GET /api/audit/events?from=...&to=...   — time-range query
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditService auditService;

    /**
     * GET /api/audit/trip/{tripId}
     *
     * Returns the complete chronological audit trail for a trip.
     *
     * Example use case:
     *   Employee claims "cab never came" → query trip audit log →
     *   PICKUP_ARRIVED record shows timestamp + GPS coordinates as evidence.
     *
     * @param tripId the trip ID to query
     * @return ordered list of audit events (oldest first)
     */
    @GetMapping("/trip/{tripId}")
    public ResponseEntity<ApiResponse> getEventsByTrip(@PathVariable Long tripId) {
        log.info("AUDIT API: GET events for trip #{}", tripId);
        List<EventLog> events = auditService.getEventsByTripId(tripId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Found " + events.size() + " audit event(s) for trip #" + tripId)
                .data(toResponseList(events))
                .build());
    }

    /**
     * GET /api/audit/vehicle/{vehicleId}
     *
     * Returns all audit events for a vehicle, newest first.
     *
     * Example use case:
     *   "How many TRIP_COMPLETED events did vehicle #1 have today?"
     *
     * @param vehicleId the vehicle ID to query
     * @return list of events (newest first)
     */
    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<ApiResponse> getEventsByVehicle(@PathVariable Long vehicleId) {
        log.info("AUDIT API: GET events for vehicle #{}", vehicleId);
        List<EventLog> events = auditService.getEventsByVehicleId(vehicleId);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Found " + events.size() + " audit event(s) for vehicle #" + vehicleId)
                .data(toResponseList(events))
                .build());
    }

    /**
     * GET /api/audit/events?from=2026-02-23T06:00:00&to=2026-02-23T10:00:00
     *
     * Returns all audit events with eventTimestamp in [from, to].
     *
     * Example use cases:
     *   - Morning shift SLA report: from=06:00 to=10:00
     *   - Evening shift billing: from=17:00 to=21:00
     *
     * Datetime format: ISO 8601 — yyyy-MM-ddTHH:mm:ss
     *
     * @param from inclusive start datetime
     * @param to   inclusive end datetime
     * @return events in the time window, oldest first
     */
    @GetMapping("/events")
    public ResponseEntity<ApiResponse> getEventsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("AUDIT API: GET events from {} to {}", from, to);

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .success(false)
                    .message("'from' must not be after 'to'. Received: from=" + from + ", to=" + to)
                    .build());
        }

        List<EventLog> events = auditService.getEventsByTimeRange(from, to);
        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Found " + events.size() + " audit event(s) between " + from + " and " + to)
                .data(toResponseList(events))
                .build());
    }

    /**
     * Converts EventLog entities to response maps.
     * Serializes the eventType enum as its String name.
     * Separates eventTimestamp (when event occurred) from createdAt (when record was written).
     */
    private List<Map<String, Object>> toResponseList(List<EventLog> events) {
        return events.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",             e.getId());
            m.put("vehicleId",      e.getVehicleId());
            m.put("tripId",         e.getTripId());
            m.put("eventType",      e.getEventType().name());      // typed enum → String
            m.put("latitude",       e.getLatitude());
            m.put("longitude",      e.getLongitude());
            m.put("eventTimestamp", e.getTimestamp() != null ? e.getTimestamp().toString() : null);
            m.put("createdAt",      e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            return m;
        }).toList();
    }
}
