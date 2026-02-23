package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.dto.ManualCloseRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import com.moveinsync.vehicletracking.service.NotificationService;
import com.moveinsync.vehicletracking.util.GeofenceUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * EC4 — Manual Trip Closure Outside Office Geofence
 *
 * Handles cases where a driver/admin closes a trip manually.
 * If outside geofence: logs discrepancy + fires ADMIN_ALERT for compliance.
 */
@RestController
@RequestMapping("/api/trip")
@RequiredArgsConstructor
@Slf4j
public class TripController {

    private final TripRepository tripRepository;
    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final EventLogRepository eventLogRepository;
    private final NotificationService notificationService;

    /**
     * POST /api/trip/{tripId}/manual-close
     *
     * Closes a trip manually regardless of vehicle location.
     * - If OUTSIDE office geofence → logs MANUAL_CLOSURE_OUTSIDE_GEOFENCE + ADMIN_ALERT
     * - If INSIDE office geofence  → logs MANUAL_CLOSURE (no discrepancy)
     *
     * Both cases finalize the trip with endTime and duration.
     */
    @PostMapping("/{tripId}/manual-close")
    @Transactional
    public ResponseEntity<ApiResponse> manualClose(
            @PathVariable Long tripId,
            @Valid @RequestBody ManualCloseRequest req) {

        log.info("EC4: Manual close requested for trip #{} at ({}, {}), reason: {}",
                tripId, req.getLatitude(), req.getLongitude(), req.getReason());

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        if ("COMPLETED".equals(trip.getStatus())) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.builder()
                            .success(false)
                            .message("Trip #" + tripId + " is already completed")
                            .build());
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isInsideGeofence = false;

        List<OfficeGeofence> geofences = officeGeofenceRepository.findAll();
        if (!geofences.isEmpty()) {
            OfficeGeofence office = geofences.get(0);
            isInsideGeofence = GeofenceUtil.isWithinRadius(
                    req.getLatitude(), req.getLongitude(),
                    office.getLatitude(), office.getLongitude(),
                    office.getRadiusMeters());
        }

        if (!isInsideGeofence) {
            // EC4 — Discrepancy detected: manual close outside geofence
            log.warn("EC4: Trip #{} manually closed OUTSIDE office geofence! ({}, {})",
                    tripId, req.getLatitude(), req.getLongitude());

            // Log the discrepancy for audit trail
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(trip.getVehicle().getId())
                    .tripId(tripId)
                    .eventType(GeofenceEventType.MANUAL_CLOSURE_OUTSIDE_GEOFENCE)
                    .latitude(req.getLatitude())
                    .longitude(req.getLongitude())
                    .timestamp(now)
                    .build());

            // Fire ADMIN_ALERT for compliance monitoring
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(trip.getVehicle().getId())
                    .tripId(tripId)
                    .eventType(GeofenceEventType.ADMIN_ALERT)
                    .latitude(req.getLatitude())
                    .longitude(req.getLongitude())
                    .timestamp(now)
                    .build());

            log.warn("[ADMIN ALERT] Trip #{} closed outside geofence. Reason: '{}'. Operations team notified.",
                    tripId, req.getReason() != null ? req.getReason() : "Not specified");

            // EC4 — Fire admin alert via NotificationService (was missing — bug fix)
            notificationService.sendAdminAlert(
                    trip.getVehicle().getId(), tripId,
                    req.getReason() != null ? req.getReason() : "Manual closure outside geofence");
        } else {
            // Normal manual close — inside geofence
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(trip.getVehicle().getId())
                    .tripId(tripId)
                    .eventType(GeofenceEventType.MANUAL_CLOSURE)
                    .latitude(req.getLatitude())
                    .longitude(req.getLongitude())
                    .timestamp(now)
                    .build());
        }

        // Finalize trip in both cases
        long durationMinutes = ChronoUnit.MINUTES.between(trip.getStartTime(), now);
        trip.setStatus("COMPLETED");
        trip.setEndTime(now);
        trip.setDurationMinutes((int) durationMinutes);
        trip.setOfficeEntryTime(null);
        tripRepository.save(trip);

        String msg = isInsideGeofence
                ? "Trip manually closed inside office geofence"
                : "Trip manually closed OUTSIDE geofence — discrepancy logged, admin alerted";

        log.info("Trip #{} manually closed. Inside geofence: {}, duration: {} min", tripId, isInsideGeofence, durationMinutes);
        return ResponseEntity.ok(ApiResponse.builder().success(true).message(msg).build());
    }
}
