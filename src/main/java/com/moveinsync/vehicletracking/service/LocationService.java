package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import com.moveinsync.vehicletracking.util.GeofenceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service class for processing vehicle location updates
 *
 * Implements all core functionalities:
 *  #1  Real-Time Location Streaming   — saves every GPS ping + pushes to WebSocket
 *  #3  Auto Trip Closure              — tracks distance, duration, auto-completes trip
 *  #4  Pickup Geofence Detection      — detects PICKUP_ARRIVED
 *  #5  Push Notification              — calls NotificationService on key events
 *  #7  Geofence Event Engine          — fires PICKUP_ARRIVED, OFFICE_REACHED, TRIP_COMPLETED
 *  #8  Audit Logging                  — every event persisted to event_logs table
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationLogRepository locationLogRepository;
    private final TripRepository tripRepository;
    private final PickupPointRepository pickupPointRepository;
    private final CacheableDataService cacheableDataService;
    private final EventLogRepository eventLogRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /** EC3 — Configurable dwell time threshold (default: 30 seconds) */
    @Value("${geofence.office.dwell-time-seconds:30}")
    private int dwellTimeSeconds;

    /** EC3 — Speed must be below this (km/h) to qualify for trip closure */
    @Value("${geofence.office.speed-threshold-kmh:5.0}")
    private double speedThresholdKmh;

    /**
     * Main entry point. Called on every GPS ping from a vehicle.
     *
     * Steps:
     * 1. Capture previous log (for distance calc) BEFORE saving current
     * 2. Load trip
     * 3. Save location log
     * 4. Calculate & update incremental distance on the trip
     * 5. Check pickup geofence  → PICKUP_ARRIVED event + push notification
     * 6. Check office geofence  → OFFICE_REACHED + TRIP_COMPLETED events + notification
     * 7. Push real-time update over WebSocket to dashboard
     */
    @Transactional
    public void processLocationUpdate(LocationUpdateRequest request) {
        log.info("Processing location update — vehicle: {}, trip: {}, speed: {} km/h",
                request.getVehicleId(), request.getTripId(), request.getSpeed());

        // Step 1: Capture previous log BEFORE saving current (needed for distance calc)
        Optional<LocationLog> prevLog = locationLogRepository
                .findTopByTripIdOrderByTimestampDesc(request.getTripId());

        // Step 2: Load trip with a PESSIMISTIC_WRITE lock (EC6 — prevents concurrent
        //         threads from both closing the same trip when two pings arrive at once)
        Trip trip = tripRepository.findByIdForUpdate(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + request.getTripId()));

        // Step 3: Save current location log (audit trail — Functionality #8)
        LocationLog locationLog = LocationLog.builder()
                .vehicleId(request.getVehicleId())
                .tripId(request.getTripId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speed(request.getSpeed())
                .timestamp(request.getTimestamp())
                .build();
        locationLogRepository.save(locationLog);

        // Step 4: Calculate incremental distance (Functionality #3)
        if (prevLog.isPresent()) {
            LocationLog prev = prevLog.get();
            double distanceKm = GeofenceUtil.calculateDistance(
                    prev.getLatitude(), prev.getLongitude(),
                    request.getLatitude(), request.getLongitude()) / 1000.0;
            double currentTotal = trip.getTotalDistanceKm() != null ? trip.getTotalDistanceKm() : 0.0;
            trip.setTotalDistanceKm(currentTotal + distanceKm);
            tripRepository.save(trip);
            log.debug("Distance updated — total: {} km", String.format("%.3f", trip.getTotalDistanceKm()));
        }

        // Step 5: Check pickup geofence (Functionality #4 + #5 + #7)
        checkPickupGeofence(trip, request);

        // Step 6: Check office geofence (Functionality #3 + #5 + #7)
        checkOfficeGeofence(trip, request);

        // Step 7: Push live update over WebSocket (Functionality #1 + #6)
        pushWebSocketUpdate(request, trip);

        log.info("Location update processed successfully for trip #{}", trip.getId());
    }

    /**
     * EC2  — Idempotency: each pickup uses its own status flag ("ARRIVED") — GPS drift safe
     * EC5  — Multi-stop: iterates ALL pickup points for the trip, not just the first
     * Functionality #4 — Pickup Geofence Detection
     * Functionality #5 — Push Notification on pickup
     * Functionality #7 — PICKUP_ARRIVED event
     *
     * Fix: was using findByTripId (single result — throws for multi-stop).
     *      Now uses findAllByTripId and checks each pickup individually.
     */
    private void checkPickupGeofence(Trip trip, LocationUpdateRequest request) {
        List<PickupPoint> pickupPoints = pickupPointRepository.findAllByTripId(trip.getId());
        if (pickupPoints.isEmpty()) {
            log.debug("No pickup points found for trip: {}", trip.getId());
            return;
        }

        for (PickupPoint pickupPoint : pickupPoints) {
            // EC2/EC6 — Idempotent: already arrived at this pickup, skip
            if ("ARRIVED".equals(pickupPoint.getStatus())) {
                continue;
            }

            boolean isInside = GeofenceUtil.isWithinRadius(
                    request.getLatitude(), request.getLongitude(),
                    pickupPoint.getLatitude(), pickupPoint.getLongitude(),
                    pickupPoint.getRadiusMeters());

            log.debug("Pickup #{} geofence check — inside: {}, status: {}",
                    pickupPoint.getId(), isInside, pickupPoint.getStatus());

            if (isInside) {
                log.info("==> PICKUP_ARRIVED triggered for trip #{}, pickup point #{}",
                        trip.getId(), pickupPoint.getId());

                pickupPoint.setStatus("ARRIVED");
                pickupPointRepository.save(pickupPoint);

                // Audit log — server-timestamped, typed enum (Functionality #8)
                logAuditEvent(request.getVehicleId(), request.getTripId(),
                        GeofenceEventType.PICKUP_ARRIVED,
                        request.getLatitude(), request.getLongitude());

                // Push notification + SMS fallback (Functionality #5)
                notificationService.sendPickupArrivalNotification(
                        request.getVehicleId(), request.getTripId(),
                        request.getLatitude(), request.getLongitude());

                // Push geofence event over WebSocket for instant dashboard update
                pushGeofenceEvent(request.getVehicleId(), request.getTripId(),
                        trip.getVehicle().getRegistrationNumber(), "PICKUP_ARRIVED",
                        request.getLatitude(), request.getLongitude());
            }
        }
    }

    /**

    private boolean isVehicleInsideGeofence(double lat, double lon, OfficeGeofence geofence) {
        return GeofenceUtil.isWithinRadius(lat, lon,
                geofence.getLatitude(), geofence.getLongitude(),
                geofence.getRadiusMeters());
    }

    private void checkOfficeGeofence(Trip trip, LocationUpdateRequest request) {
        List<OfficeGeofence> officeGeofences = cacheableDataService.getOfficeGeofences();
        if (officeGeofences.isEmpty()) {
            log.debug("No office geofence configured");
            return;
        }

        // Support multiple office geofences — match first one the vehicle is inside
        Optional<OfficeGeofence> matchedGeofence = officeGeofences.stream()
                .filter(g -> isVehicleInsideGeofence(request.getLatitude(), request.getLongitude(), g))
                .findFirst();
        boolean isInside = matchedGeofence.isPresent();

        // EC2/EC3 — Vehicle exited geofence: reset dwell timer so GPS drift can't re-trigger
        if (!isInside && trip.getOfficeEntryTime() != null && "IN_PROGRESS".equals(trip.getStatus())) {
            log.info("EC2/EC3: Vehicle exited office geofence for trip #{} — resetting dwell timer", trip.getId());
            trip.setOfficeEntryTime(null);
            tripRepository.save(trip);
            // Audit: log the geofence exit for compliance tracking
            logAuditEvent(request.getVehicleId(), request.getTripId(),
                    GeofenceEventType.GEOFENCE_EXIT,
                    request.getLatitude(), request.getLongitude());
            return;
        }

        if (!isInside) return;

        // EC6/EC2 — Idempotent: skip if trip already closed
        if (!"IN_PROGRESS".equals(trip.getStatus())) {
            log.debug("EC6: Trip #{} already '{}' — skipping office check", trip.getId(), trip.getStatus());
            return;
        }

        // EC3 — Record entry time on first entry
        if (trip.getOfficeEntryTime() == null) {
            trip.setOfficeEntryTime(LocalDateTime.now());
            tripRepository.save(trip);
            log.info("EC3: Vehicle entered office geofence for trip #{} — dwell timer started (need {}s)",
                    trip.getId(), dwellTimeSeconds);
            return; // wait for next ping to validate dwell time
        }

        // EC3 — Check dwell time: must be inside for at least dwellTimeSeconds
        long secondsInGeofence = ChronoUnit.SECONDS.between(trip.getOfficeEntryTime(), LocalDateTime.now());
        if (secondsInGeofence < dwellTimeSeconds) {
            log.debug("EC3: Dwell time not met for trip #{} — {}s / {}s",
                    trip.getId(), secondsInGeofence, dwellTimeSeconds);
            return;
        }

        // EC3 — Drive-through protection: vehicle must be slow (not just passing)
        if (request.getSpeed() >= speedThresholdKmh) {
            log.debug("EC3: Speed {} km/h >= threshold {} km/h for trip #{} — drive-through protection active",
                    String.format("%.1f", request.getSpeed()), speedThresholdKmh, trip.getId());
            return;
        }

        // EC5 — Multi-stop: all pickup points must be ARRIVED before closing
        List<PickupPoint> allPickups = pickupPointRepository.findAllByTripId(trip.getId());
        if (!allPickups.isEmpty()) {
            List<PickupPoint> pending = allPickups.stream()
                    .filter(pp -> !"ARRIVED".equals(pp.getStatus())).toList();
            if (!pending.isEmpty()) {
                log.warn("EC5: Trip #{} cannot auto-close — {}/{} pickup(s) still PENDING",
                        trip.getId(), pending.size(), allPickups.size());
                logAuditEvent(request.getVehicleId(), request.getTripId(),
                        GeofenceEventType.TRIP_CLOSURE_BLOCKED_PENDING_PICKUPS,
                        request.getLatitude(), request.getLongitude());
                return;
            }
        }

        // All checks passed — auto-close the trip
        // EC6 — Secondary idempotency guard: confirm OFFICE_REACHED was never logged
        //        (defence-in-depth on top of the trip status check above)
        if (eventLogRepository.existsByTripIdAndEventType(request.getTripId(), GeofenceEventType.OFFICE_REACHED)) {
            log.warn("EC6: OFFICE_REACHED already logged for trip #{} — skipping duplicate closure",
                    trip.getId());
            return;
        }

        log.info("==> OFFICE_REACHED + TRIP_COMPLETED triggered for trip #{} (dwell: {}s, speed: {} km/h)",
                trip.getId(), secondsInGeofence, request.getSpeed());

        LocalDateTime now = LocalDateTime.now();

        logAuditEvent(request.getVehicleId(), request.getTripId(),
                GeofenceEventType.OFFICE_REACHED,
                request.getLatitude(), request.getLongitude());

        long durationMinutes = ChronoUnit.MINUTES.between(trip.getStartTime(), now);
        trip.setStatus("COMPLETED");
        trip.setEndTime(now);
        trip.setDurationMinutes((int) durationMinutes);
        trip.setOfficeEntryTime(null);
        tripRepository.save(trip);

        logAuditEvent(request.getVehicleId(), request.getTripId(),
                GeofenceEventType.TRIP_COMPLETED,
                request.getLatitude(), request.getLongitude());

        notificationService.sendTripCompletionNotification(request.getVehicleId(), request.getTripId());

        // Push geofence event over WebSocket so dashboard updates immediately
        pushGeofenceEvent(request.getVehicleId(), request.getTripId(),
                trip.getVehicle().getRegistrationNumber(), "TRIP_COMPLETED",
                request.getLatitude(), request.getLongitude());

        log.info("Trip #{} auto-closed — duration: {} min, distance: {} km",
                trip.getId(), durationMinutes,
                trip.getTotalDistanceKm() != null
                        ? String.format("%.2f", trip.getTotalDistanceKm()) : "0.00");
    }

    /**
     * Pushes a typed geofence event to /topic/geofence-events so the dashboard
     * can react instantly (pickup arrived, trip completed, etc.) without waiting
     * for the next polling cycle.
     */
    private void pushGeofenceEvent(Long vehicleId, Long tripId, String vehicleReg,
                                   String eventType, double lat, double lon) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("eventType", eventType);
        msg.put("vehicleId", vehicleId);
        msg.put("tripId", tripId);
        msg.put("vehicleReg", vehicleReg);
        msg.put("latitude", lat);
        msg.put("longitude", lon);
        msg.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/geofence-events", msg);
        log.info("WS geofence-event: {} — vehicle {} trip {}", eventType, vehicleReg, tripId);
    }

    /**
     * Audit & Compliance — Centralized event persistence helper.
     *
     * Design decisions:
     *  1. SERVER timestamp only — LocalDateTime.now() is always server time.
     *     The device/request timestamp is NEVER used for audit records.
     *  2. Error handling — DB write failures are caught and logged.
     *     The exception is NOT rethrown so that a transient audit failure
     *     does not abort the main geofence operation.
     *  3. Synchronous — runs in the same @Transactional boundary as the
     *     calling method. This ensures atomicity: if the trip status update
     *     commits, the audit record also commits.
     *
     * Production note: For extremely high throughput (10k+ vehicles), replace
     * the direct DB save with an async Kafka publish. A separate audit consumer
     * would then persist to a dedicated audit DB without blocking GPS processing.
     *
     * @param vehicleId vehicle that triggered the event
     * @param tripId    trip associated with the event
     * @param eventType typed enum — no raw strings
     * @param latitude  GPS latitude at event time
     * @param longitude GPS longitude at event time
     */
    private void logAuditEvent(Long vehicleId, Long tripId, GeofenceEventType eventType,
                                double latitude, double longitude) {
        try {
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(vehicleId)
                    .tripId(tripId)
                    .eventType(eventType)
                    .latitude(latitude)
                    .longitude(longitude)
                    .timestamp(LocalDateTime.now())   // SERVER time — never device time
                    .build());
            log.info("AUDIT: {} persisted — vehicle: {}, trip: {}, loc: ({}, {})",
                    eventType, vehicleId, tripId,
                    String.format("%.5f", latitude), String.format("%.5f", longitude));
        } catch (Exception e) {
            // Audit failure must NOT abort the main geofence operation.
            // The error is logged for investigation and alerting.
            log.error("AUDIT: Failed to persist {} for trip #{} — {}", eventType, tripId, e.getMessage());
        }
    }

    /**
     * Functionality #1 — Real-Time Location Streaming via WebSocket
     * Functionality #6 — Push live updates to dashboard (not polling)
     *
     * Pushes every GPS ping immediately to all subscribed dashboard clients
     * via STOMP topic /topic/location-updates
     */
    private void pushWebSocketUpdate(LocationUpdateRequest request, Trip trip) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("vehicleId", request.getVehicleId());
        message.put("tripId", request.getTripId());
        message.put("vehicleReg", trip.getVehicle().getRegistrationNumber());
        message.put("latitude", request.getLatitude());
        message.put("longitude", request.getLongitude());
        message.put("speed", request.getSpeed());
        message.put("timestamp", request.getTimestamp().toString());
        message.put("tripStatus", trip.getStatus());
        message.put("totalDistanceKm", trip.getTotalDistanceKm() != null
                ? Math.round(trip.getTotalDistanceKm() * 1000.0) / 1000.0 : 0.0);

        messagingTemplate.convertAndSend("/topic/location-updates", message);
        log.debug("WebSocket pushed to /topic/location-updates");
    }

}
