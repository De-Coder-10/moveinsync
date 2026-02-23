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
    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final EventLogRepository eventLogRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

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

        // Step 2: Load trip
        Trip trip = tripRepository.findById(request.getTripId())
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
     * Functionality #4 — Pickup Geofence Detection
     * Functionality #5 — Push Notification on pickup
     * Functionality #7 — PICKUP_ARRIVED event
     */
    private void checkPickupGeofence(Trip trip, LocationUpdateRequest request) {
        PickupPoint pickupPoint = pickupPointRepository.findByTripId(trip.getId()).orElse(null);
        if (pickupPoint == null) {
            log.debug("No pickup point found for trip: {}", trip.getId());
            return;
        }

        boolean isInside = GeofenceUtil.isWithinRadius(
                request.getLatitude(), request.getLongitude(),
                pickupPoint.getLatitude(), pickupPoint.getLongitude(),
                pickupPoint.getRadiusMeters());

        log.debug("Pickup geofence — inside: {}, status: {}", isInside, pickupPoint.getStatus());

        if (isInside && !"ARRIVED".equals(pickupPoint.getStatus())) {
            log.info("==> PICKUP_ARRIVED triggered for trip #{}", trip.getId());

            pickupPoint.setStatus("ARRIVED");
            pickupPointRepository.save(pickupPoint);

            // Audit log (Functionality #8)
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(request.getVehicleId())
                    .tripId(request.getTripId())
                    .eventType("PICKUP_ARRIVED")
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .timestamp(LocalDateTime.now())
                    .build());

            // Push notification + SMS fallback (Functionality #5)
            notificationService.sendPickupArrivalNotification(
                    request.getVehicleId(), request.getTripId(),
                    request.getLatitude(), request.getLongitude());
        }
    }

    /**
     * Functionality #3 — Auto Trip Closure (distance, duration, end timestamp)
     * Functionality #5 — Push notification on completion
     * Functionality #7 — OFFICE_REACHED + TRIP_COMPLETED events (two separate events per spec)
     *
     * Conditions for auto-closure:
     *  - Vehicle inside office geofence
     *  - Trip status = IN_PROGRESS
     *  - Speed < 5 km/h (vehicle has stopped — prevents false trigger while passing by)
     */
    private void checkOfficeGeofence(Trip trip, LocationUpdateRequest request) {
        List<OfficeGeofence> officeGeofences = officeGeofenceRepository.findAll();
        if (officeGeofences.isEmpty()) {
            log.debug("No office geofence configured");
            return;
        }

        OfficeGeofence officeGeofence = officeGeofences.get(0);

        boolean isInside = GeofenceUtil.isWithinRadius(
                request.getLatitude(), request.getLongitude(),
                officeGeofence.getLatitude(), officeGeofence.getLongitude(),
                officeGeofence.getRadiusMeters());

        log.debug("Office geofence — inside: {}, status: {}, speed: {} km/h",
                isInside, trip.getStatus(), request.getSpeed());

        boolean shouldComplete = isInside
                && "IN_PROGRESS".equals(trip.getStatus())
                && request.getSpeed() < 5.0;

        if (shouldComplete) {
            log.info("==> OFFICE_REACHED + TRIP_COMPLETED triggered for trip #{}", trip.getId());

            LocalDateTime now = LocalDateTime.now();

            // Event 1: OFFICE_REACHED (required by spec as separate event)
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(request.getVehicleId())
                    .tripId(request.getTripId())
                    .eventType("OFFICE_REACHED")
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .timestamp(now)
                    .build());

            // Finalize trip — record end time, duration, distance (Functionality #3)
            long durationMinutes = ChronoUnit.MINUTES.between(trip.getStartTime(), now);
            trip.setStatus("COMPLETED");
            trip.setEndTime(now);
            trip.setDurationMinutes((int) durationMinutes);
            tripRepository.save(trip);

            // Event 2: TRIP_COMPLETED
            eventLogRepository.save(EventLog.builder()
                    .vehicleId(request.getVehicleId())
                    .tripId(request.getTripId())
                    .eventType("TRIP_COMPLETED")
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .timestamp(now)
                    .build());

            // Push notification (Functionality #5)
            notificationService.sendTripCompletionNotification(request.getVehicleId(), request.getTripId());

            log.info("Trip #{} auto-closed — duration: {} min, distance: {} km",
                    trip.getId(), durationMinutes,
                    trip.getTotalDistanceKm() != null
                            ? String.format("%.2f", trip.getTotalDistanceKm()) : "0.00");
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
