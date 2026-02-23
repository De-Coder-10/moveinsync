package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * REST Controller providing dashboard data and simulation reset
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardApiController {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final PickupPointRepository pickupPointRepository;
    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final LocationLogRepository locationLogRepository;
    private final EventLogRepository eventLogRepository;

    /**
     * Returns all data needed by the dashboard in a single call
     */
    @GetMapping("/data")
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();

        // Vehicles
        List<Map<String, Object>> vehicles = vehicleRepository.findAll().stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("registrationNumber", v.getRegistrationNumber());
                    m.put("status", v.getStatus());
                    return m;
                }).toList();
        data.put("vehicles", vehicles);

        // Trips (resolve lazy vehicle reference manually)
        List<Map<String, Object>> trips = tripRepository.findAll().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("vehicleId", t.getVehicle().getId());
                    m.put("vehicleReg", t.getVehicle().getRegistrationNumber());
                    m.put("status", t.getStatus());
                    m.put("startTime", t.getStartTime() != null ? t.getStartTime().toString() : null);
                    m.put("endTime", t.getEndTime() != null ? t.getEndTime().toString() : null);
                    return m;
                }).toList();
        data.put("trips", trips);

        // Pickup Points
        List<Map<String, Object>> pickups = pickupPointRepository.findAll().stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("tripId", p.getTrip().getId());
                    m.put("latitude", p.getLatitude());
                    m.put("longitude", p.getLongitude());
                    m.put("radiusMeters", p.getRadiusMeters());
                    m.put("status", p.getStatus());
                    return m;
                }).toList();
        data.put("pickupPoints", pickups);

        // Office Geofences
        List<Map<String, Object>> offices = officeGeofenceRepository.findAll().stream()
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("latitude", o.getLatitude());
                    m.put("longitude", o.getLongitude());
                    m.put("radiusMeters", o.getRadiusMeters());
                    return m;
                }).toList();
        data.put("officeGeofences", offices);

        // Location logs for first trip (route trail)
        Long tripId = trips.isEmpty() ? null : (Long) trips.get(0).get("id");
        List<Map<String, Object>> locations = Collections.emptyList();
        if (tripId != null) {
            locations = locationLogRepository.findByTripIdOrderByTimestampAsc(tripId).stream()
                    .map(l -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", l.getId());
                        m.put("vehicleId", l.getVehicleId());
                        m.put("tripId", l.getTripId());
                        m.put("latitude", l.getLatitude());
                        m.put("longitude", l.getLongitude());
                        m.put("speed", l.getSpeed());
                        m.put("timestamp", l.getTimestamp().toString());
                        return m;
                    }).toList();
        }
        data.put("locationLogs", locations);

        // Events (newest first)
        List<Map<String, Object>> events = eventLogRepository.findAll().stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("vehicleId", e.getVehicleId());
                    m.put("tripId", e.getTripId());
                    m.put("eventType", e.getEventType());
                    m.put("latitude", e.getLatitude());
                    m.put("longitude", e.getLongitude());
                    m.put("timestamp", e.getTimestamp().toString());
                    return m;
                }).toList();
        data.put("events", events);

        return data;
    }

    /**
     * Resets the trip so the user can re-run the simulation
     */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<ApiResponse> resetTrip() {
        List<Trip> trips = tripRepository.findAll();
        if (trips.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.builder().success(false).message("No trips found").build()
            );
        }

        Trip trip = trips.get(0);
        Long tripId = trip.getId();
        log.info("Resetting trip ID: {}", tripId);

        // Delete location logs for this trip
        List<LocationLog> logs = locationLogRepository.findByTripIdOrderByTimestampAsc(tripId);
        locationLogRepository.deleteAll(logs);

        // Delete events for this trip
        List<EventLog> events = eventLogRepository.findByTripId(tripId);
        eventLogRepository.deleteAll(events);

        // Reset trip status
        trip.setStatus("IN_PROGRESS");
        trip.setEndTime(null);
        trip.setStartTime(LocalDateTime.now());
        tripRepository.save(trip);

        // Reset pickup point
        pickupPointRepository.findByTripId(tripId).ifPresent(pp -> {
            pp.setStatus("PENDING");
            pickupPointRepository.save(pp);
        });

        log.info("Trip {} reset successfully", tripId);

        return ResponseEntity.ok(
                ApiResponse.builder().success(true).message("Trip reset successfully").build()
        );
    }
}
