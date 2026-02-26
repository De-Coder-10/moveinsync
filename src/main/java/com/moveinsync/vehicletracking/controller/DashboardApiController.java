package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import com.moveinsync.vehicletracking.service.AutoSimulationService;
import com.moveinsync.vehicletracking.service.CacheableDataService;
import com.moveinsync.vehicletracking.util.GeofenceUtil;
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

    private final TripRepository tripRepository;
    private final PickupPointRepository pickupPointRepository;
    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final LocationLogRepository locationLogRepository;
    private final EventLogRepository eventLogRepository;
    private final AutoSimulationService autoSimulationService;
    private final CacheableDataService cacheableDataService;

    /**
     * Returns all data needed by the dashboard in a single call
     */
    @GetMapping("/data")
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();

        // Vehicles — served from Caffeine cache (vehicleDriverData) after first load
        List<Map<String, Object>> vehicles = cacheableDataService.getVehicles().stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", v.getId());
                    m.put("registrationNumber", v.getRegistrationNumber());
                    m.put("status", v.getStatus());
                    return m;
                }).toList();
        data.put("vehicles", vehicles);

        // Trips (resolve lazy vehicle reference manually)
        // Get latest location log for ETA calculation
        List<LocationLog> allLogs = locationLogRepository.findAll();
        // Office geofences — served from Caffeine cache (officeGeofences) after first load
        List<OfficeGeofence> offices = cacheableDataService.getOfficeGeofences();
        List<PickupPoint> allPickups = pickupPointRepository.findAll();

        List<Map<String, Object>> trips = tripRepository.findAll().stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("vehicleId", t.getVehicle().getId());
                    m.put("vehicleReg", t.getVehicle().getRegistrationNumber());
                    m.put("status", t.getStatus());
                    m.put("startTime", t.getStartTime() != null ? t.getStartTime().toString() : null);
                    m.put("endTime", t.getEndTime() != null ? t.getEndTime().toString() : null);
                    m.put("totalDistanceKm", t.getTotalDistanceKm() != null
                            ? Math.round(t.getTotalDistanceKm() * 100.0) / 100.0 : 0.0);
                    m.put("durationMinutes", t.getDurationMinutes() != null ? t.getDurationMinutes() : 0);

                    // Driver info — served from Caffeine cache (vehicleDriverData) keyed by vehicleId
                    cacheableDataService.getDriverByVehicleId(t.getVehicle().getId()).ifPresent(d -> {
                        m.put("driverName", d.getName());
                        m.put("driverPhone", d.getPhoneNumber());
                        m.put("driverLicense", d.getLicenseNumber());
                    });

                    // Region / office / route metadata (for dashboard filters)
                    String[] meta = AutoSimulationService.getRouteMeta(t.getVehicle().getRegistrationNumber());
                    m.put("region", meta[0]);
                    m.put("officeName", meta[1]);
                    m.put("route", meta[2]);

                    // Current speed from latest location log
                    Optional<LocationLog> latestLog = allLogs.stream()
                            .filter(l -> t.getId().equals(l.getTripId()))
                            .max(Comparator.comparing(LocationLog::getTimestamp));
                    m.put("currentSpeed", latestLog.map(LocationLog::getSpeed).orElse(0.0));

                    if (latestLog.isPresent() && "IN_PROGRESS".equals(t.getStatus())) {
                        LocationLog ll = latestLog.get();
                        double speed = ll.getSpeed() > 2.0 ? ll.getSpeed() : 30.0;

                        // ETA to pickup if still PENDING, else ETA to office
                        Optional<PickupPoint> tripPickup = allPickups.stream()
                                .filter(pp -> t.getId().equals(pp.getTrip().getId()))
                                .findFirst();

                        if (tripPickup.isPresent() && "PENDING".equals(tripPickup.get().getStatus())) {
                            PickupPoint pp = tripPickup.get();
                            double distKm = GeofenceUtil.calculateDistance(
                                    ll.getLatitude(), ll.getLongitude(),
                                    pp.getLatitude(), pp.getLongitude()) / 1000.0;
                            long etaMin = Math.round((distKm / speed) * 60);
                            m.put("etaMinutes", etaMin);
                            m.put("etaDestination", "Pickup");
                        } else if (!offices.isEmpty()) {
                            OfficeGeofence of = offices.get(0);
                            double distKm = GeofenceUtil.calculateDistance(
                                    ll.getLatitude(), ll.getLongitude(),
                                    of.getLatitude(), of.getLongitude()) / 1000.0;
                            long etaMin = Math.round((distKm / speed) * 60);
                            m.put("etaMinutes", etaMin);
                            m.put("etaDestination", "Office");
                        }
                    } else {
                        m.put("etaMinutes", null);
                        m.put("etaDestination", null);
                    }
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

        // Office Geofences (reuse already-loaded offices list)
        List<Map<String, Object>> officesList = offices.stream()
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("name", o.getName() != null ? o.getName() : "Office");
                    m.put("latitude", o.getLatitude());
                    m.put("longitude", o.getLongitude());
                    m.put("radiusMeters", o.getRadiusMeters());
                    return m;
                }).toList();
        data.put("officeGeofences", officesList);

        // Location logs for ALL trips (used by frontend to reconstruct vehicle trails)
        List<Map<String, Object>> locations = locationLogRepository.findAll().stream()
                .sorted(Comparator.comparing(LocationLog::getTimestamp))
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
        data.put("locationLogs", locations);

        // Events (newest first)
        List<Map<String, Object>> events = eventLogRepository.findAll().stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("vehicleId", e.getVehicleId());
                    m.put("tripId", e.getTripId());
                    m.put("eventType", e.getEventType() != null ? e.getEventType().name() : null);
                    m.put("latitude", e.getLatitude());
                    m.put("longitude", e.getLongitude());
                    m.put("timestamp", e.getTimestamp().toString());
                    m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
                    return m;
                }).toList();
        data.put("events", events);

        return data;
    }

    /**
     * Starts the simulation for a specific trip.
     * Fetches the OSRM road route and begins advancing the vehicle automatically.
     * The trip ends automatically when it enters the office geofence.
     */
    @PostMapping("/start-trip/{tripId}")
    public ResponseEntity<ApiResponse> startTrip(@PathVariable Long tripId) {
        boolean started = autoSimulationService.startTrip(tripId);
        if (started) {
            log.info("Trip #{} started via dashboard API", tripId);
            return ResponseEntity.ok(
                    ApiResponse.builder().success(true)
                            .message("Trip #" + tripId + " started successfully").build());
        }
        return ResponseEntity.badRequest().body(
                ApiResponse.builder().success(false)
                        .message("Trip #" + tripId + " not found").build());
    }

    /**
     * Resets ALL trips back to PENDING so they can be started manually again.
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

        // Clear in-memory simulation state (waypoint indices, reset timers)
        autoSimulationService.resetAll();
        // Evict static caches so next dashboard poll reloads fresh data from DB
        cacheableDataService.evictStaticCaches();

        for (Trip trip : trips) {
            Long tripId = trip.getId();
            log.info("Resetting trip ID: {}", tripId);

            locationLogRepository.deleteAll(locationLogRepository.findByTripIdOrderByTimestampAsc(tripId));
            eventLogRepository.deleteAll(eventLogRepository.findByTripId(tripId));

            trip.setStatus("PENDING");
            trip.setEndTime(null);
            trip.setStartTime(null);
            trip.setTotalDistanceKm(null);
            trip.setDurationMinutes(null);
            trip.setOfficeEntryTime(null);
            tripRepository.save(trip);

            pickupPointRepository.findAllByTripId(tripId).forEach(pp -> {
                pp.setStatus("PENDING");
                pickupPointRepository.save(pp);
            });
        }

        log.info("All {} trips reset successfully", trips.size());
        return ResponseEntity.ok(
                ApiResponse.builder().success(true)
                        .message("All " + trips.size() + " trips reset successfully").build()
        );
    }
}
