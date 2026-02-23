package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import com.moveinsync.vehicletracking.util.GeofenceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service class for processing vehicle location updates
 * Handles geofence checking and event generation
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

    /**
     * Process location update from a vehicle
     * This is the main method that orchestrates all geofence checks
     * 
     * Steps:
     * 1. Save location log
     * 2. Load trip
     * 3. Check pickup geofence
     * 4. Check office geofence
     * 5. Generate events as needed
     */
    @Transactional
    public void processLocationUpdate(LocationUpdateRequest request) {
        log.info("Processing location update for vehicle: {}, trip: {}", 
                request.getVehicleId(), request.getTripId());

        // Step 1: Save location log
        LocationLog locationLog = LocationLog.builder()
                .vehicleId(request.getVehicleId())
                .tripId(request.getTripId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .speed(request.getSpeed())
                .timestamp(request.getTimestamp())
                .build();
        locationLogRepository.save(locationLog);
        log.debug("Location log saved with ID: {}", locationLog.getId());

        // Step 2: Load trip by tripId
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + request.getTripId()));
        log.debug("Trip found: {}, status: {}", trip.getId(), trip.getStatus());

        // Step 3: Check pickup geofence
        checkPickupGeofence(trip, request);

        // Step 4: Check office geofence
        checkOfficeGeofence(trip, request);

        log.info("Location update processed successfully");
    }

    /**
     * Check if vehicle has arrived at pickup point
     * Only triggers once when vehicle first enters the geofence
     */
    private void checkPickupGeofence(Trip trip, LocationUpdateRequest request) {
        // Load pickup point for the trip
        PickupPoint pickupPoint = pickupPointRepository.findByTripId(trip.getId())
                .orElse(null);

        if (pickupPoint == null) {
            log.debug("No pickup point found for trip: {}", trip.getId());
            return;
        }

        // Calculate distance between vehicle and pickup point
        boolean isInside = GeofenceUtil.isWithinRadius(
                request.getLatitude(), 
                request.getLongitude(),
                pickupPoint.getLatitude(), 
                pickupPoint.getLongitude(),
                pickupPoint.getRadiusMeters()
        );

        log.debug("Pickup geofence check - Inside: {}, Status: {}", isInside, pickupPoint.getStatus());

        // If inside radius AND status is not yet ARRIVED, trigger arrival event
        if (isInside && !"ARRIVED".equals(pickupPoint.getStatus())) {
            log.info("Vehicle arrived at pickup point!");
            
            // Update pickup status
            pickupPoint.setStatus("ARRIVED");
            pickupPointRepository.save(pickupPoint);
            
            // Save event log
            EventLog eventLog = EventLog.builder()
                    .vehicleId(request.getVehicleId())
                    .tripId(request.getTripId())
                    .eventType("PICKUP_ARRIVED")
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .timestamp(LocalDateTime.now())
                    .build();
            eventLogRepository.save(eventLog);
            
            log.info("PICKUP_ARRIVED event saved with ID: {}", eventLog.getId());
        }
    }

    /**
     * Check if vehicle has reached office and should complete the trip
     * Only triggers when:
     * - Vehicle is inside office geofence
     * - Trip status is IN_PROGRESS
     * - Vehicle speed is less than 5 km/h (almost stopped)
     */
    private void checkOfficeGeofence(Trip trip, LocationUpdateRequest request) {
        // Load office geofence (assume single record)
        List<OfficeGeofence> officeGeofences = officeGeofenceRepository.findAll();
        
        if (officeGeofences.isEmpty()) {
            log.debug("No office geofence configured");
            return;
        }

        OfficeGeofence officeGeofence = officeGeofences.get(0); // Get first/only office geofence

        // Calculate distance between vehicle and office
        boolean isInside = GeofenceUtil.isWithinRadius(
                request.getLatitude(), 
                request.getLongitude(),
                officeGeofence.getLatitude(), 
                officeGeofence.getLongitude(),
                officeGeofence.getRadiusMeters()
        );

        log.debug("Office geofence check - Inside: {}, Trip status: {}, Speed: {}", 
                isInside, trip.getStatus(), request.getSpeed());

        // Check all conditions for trip completion
        boolean shouldComplete = isInside && 
                                "IN_PROGRESS".equals(trip.getStatus()) && 
                                request.getSpeed() < 5.0;

        if (shouldComplete) {
            log.info("Trip completion conditions met!");
            
            // Update trip status to COMPLETED
            trip.setStatus("COMPLETED");
            trip.setEndTime(LocalDateTime.now());
            tripRepository.save(trip);
            
            // Save event log
            EventLog eventLog = EventLog.builder()
                    .vehicleId(request.getVehicleId())
                    .tripId(request.getTripId())
                    .eventType("TRIP_COMPLETED")
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .timestamp(LocalDateTime.now())
                    .build();
            eventLogRepository.save(eventLog);
            
            log.info("TRIP_COMPLETED event saved with ID: {}", eventLog.getId());
        }
    }

}
