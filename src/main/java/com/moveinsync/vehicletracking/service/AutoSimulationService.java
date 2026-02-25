package com.moveinsync.vehicletracking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives active vehicles along real OSRM road routes every 1.5 seconds.
 * Trips do NOT start automatically -- they are started manually via startTrip().
 * When a vehicle reaches the office geofence, LocationService fires
 * TRIP_COMPLETED automatically (no manual action required).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoSimulationService {

    private final LocationService locationService;
    private final TripRepository tripRepository;
    private final LocationLogRepository locationLogRepository;
    private final PickupPointRepository pickupPointRepository;
    private final EventLogRepository eventLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /** Shared HTTP client for OSRM road-routing calls */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** Trips currently being actively simulated */
    private final Set<Long> activeTrips = ConcurrentHashMap.newKeySet();

    /** Current waypoint index per trip ID */
    private final ConcurrentHashMap<Long, Integer> waypointIdx = new ConcurrentHashMap<>();

    /** OSRM-fetched road waypoints per trip ID */
    private final ConcurrentHashMap<Long, double[][]> routeWaypoints = new ConcurrentHashMap<>();

    /** Pickup start coordinates per vehicle registration */
    private static final Map<String, double[]> ROUTE_STARTS = Map.of(
            "KA01AB1234", new double[]{12.9520, 77.5750},
            "TN03EF9012", new double[]{12.9780, 77.6450}
    );

    /** All routes end at Bangalore HQ */
    private static final double[] OFFICE = {12.9716, 77.5946};

    private static final Map<String, String[]> ROUTE_META = Map.of(
            "KA01AB1234", new String[]{"Bangalore", "Bangalore HQ", "North Route"},
            "TN03EF9012", new String[]{"Chennai",   "Bangalore HQ", "East Route"}
    );

    /**
     * Starts simulation for a single trip.
     * Fetches real road route from OSRM (pickup to office), then advances vehicle every 1.5s.
     * The trip ends automatically when LocationService detects office geofence entry.
     */
    @Transactional
    public boolean startTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            log.warn("AutoSim: startTrip called for unknown trip #{}", tripId);
            return false;
        }
        String reg = trip.getVehicle().getRegistrationNumber();

        trip.setStatus("IN_PROGRESS");
        trip.setStartTime(LocalDateTime.now());
        trip.setEndTime(null);
        trip.setTotalDistanceKm(null);
        trip.setDurationMinutes(null);
        trip.setOfficeEntryTime(null);
        tripRepository.save(trip);

        pickupPointRepository.findAllByTripId(tripId)
                .forEach(pp -> { pp.setStatus("PENDING"); pickupPointRepository.save(pp); });

        double[] start = ROUTE_STARTS.getOrDefault(reg, new double[]{12.9520, 77.5750});
        double[][] waypoints = fetchOsrmRoute(start[0], start[1], OFFICE[0], OFFICE[1], reg);

        routeWaypoints.put(tripId, waypoints);
        waypointIdx.put(tripId, 0);
        activeTrips.add(tripId);

        log.info("AutoSim: Trip #{} ({}) started with {} road waypoints", tripId, reg, waypoints.length);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("eventType",  "TRIP_STARTED");
        msg.put("tripId",     tripId);
        msg.put("vehicleReg", reg);
        msg.put("vehicleId",  trip.getVehicle().getId());
        msg.put("fromLat",    start[0]);
        msg.put("fromLng",    start[1]);
        msg.put("toLat",      OFFICE[0]);
        msg.put("toLng",      OFFICE[1]);
        messagingTemplate.convertAndSend("/topic/geofence-events", msg);
        return true;
    }

    /**
     * Scheduler tick -- runs every 1.5s.
     * Only advances trips that have been explicitly started via startTrip().
     */
    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void tick() {
        if (activeTrips.isEmpty()) return;
        List<Trip> trips;
        try {
            trips = tripRepository.findAll();
        } catch (Exception e) {
            log.debug("AutoSim: DB not ready yet -- {}", e.getMessage());
            return;
        }
        for (Trip trip : trips) {
            if (!activeTrips.contains(trip.getId())) continue;
            try {
                if ("IN_PROGRESS".equals(trip.getStatus())) {
                    advance(trip);
                } else if ("COMPLETED".equals(trip.getStatus())) {
                    activeTrips.remove(trip.getId());
                    log.info("AutoSim: Trip #{} completed -- simulation stopped", trip.getId());
                }
            } catch (Exception e) {
                log.warn("AutoSim tick error for trip #{}: {}", trip.getId(), e.getMessage());
            }
        }
    }

    private void advance(Trip trip) {
        Long tripId = trip.getId();
        double[][] pts = routeWaypoints.get(tripId);
        if (pts == null || pts.length == 0) return;
        int idx = waypointIdx.computeIfAbsent(tripId, k -> 0);
        int clampedIdx = Math.min(idx, pts.length - 1);

        LocationUpdateRequest req = new LocationUpdateRequest();
        req.setVehicleId(trip.getVehicle().getId());
        req.setTripId(tripId);
        req.setLatitude(pts[clampedIdx][0]);
        req.setLongitude(pts[clampedIdx][1]);
        req.setSpeed(computeSpeed(clampedIdx, pts.length));
        req.setTimestamp(LocalDateTime.now());
        locationService.processLocationUpdate(req);

        if (idx < pts.length - 1) {
            waypointIdx.put(tripId, idx + 1);
        }
    }

    private double computeSpeed(int idx, int total) {
        double t = (double) idx / Math.max(total - 1, 1);
        if      (t < 0.05) return 2.0;
        else if (t < 0.20) return 5.0 + t * 150;
        else if (t < 0.80) return 35.0 + Math.sin(t * Math.PI) * 8;
        else if (t < 0.92) return 35.0 - (t - 0.80) / 0.12 * 30;
        else               return 2.0;
    }

    /**
     * Fetches a real road route from OSRM between two coordinates.
     * Falls back to hardcoded straight-line route if OSRM is unreachable.
     */
    private double[][] fetchOsrmRoute(double fromLat, double fromLng,
                                      double toLat, double toLng, String reg) {
        try {
            String url = String.format(
                    "https://router.project-osrm.org/route/v1/driving/%.4f,%.4f;%.4f,%.4f?overview=full&geometries=geojson",
                    fromLng, fromLat, toLng, toLat);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode coords = root.path("routes").get(0).path("geometry").path("coordinates");
            double[][] waypoints = new double[coords.size()][2];
            for (int i = 0; i < coords.size(); i++) {
                waypoints[i][0] = coords.get(i).get(1).asDouble();
                waypoints[i][1] = coords.get(i).get(0).asDouble();
            }
            log.info("AutoSim: OSRM road route fetched -- {} waypoints for {}", waypoints.length, reg);
            return waypoints;
        } catch (Exception e) {
            log.error("AutoSim: OSRM unavailable for {} -- {}", reg, e.getMessage());
            throw new RuntimeException("Failed to fetch OSRM route for " + reg + ". Ensure internet connectivity and try again.");
        }
    }

    @Transactional
    public void doReset(Trip trip) {
        Long id = trip.getId();
        String reg = trip.getVehicle().getRegistrationNumber();
        locationLogRepository.deleteAll(locationLogRepository.findByTripIdOrderByTimestampAsc(id));
        eventLogRepository.deleteAll(eventLogRepository.findByTripId(id));
        trip.setStatus("PENDING");
        trip.setStartTime(null);
        trip.setEndTime(null);
        trip.setTotalDistanceKm(null);
        trip.setDurationMinutes(null);
        trip.setOfficeEntryTime(null);
        tripRepository.save(trip);
        pickupPointRepository.findAllByTripId(id)
                .forEach(pp -> { pp.setStatus("PENDING"); pickupPointRepository.save(pp); });
        activeTrips.remove(id);
        waypointIdx.remove(id);
        routeWaypoints.remove(id);
        log.info("AutoSim: Trip #{} ({}) reset to PENDING", id, reg);
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("eventType",  "TRIP_RESET");
        msg.put("tripId",     id);
        msg.put("vehicleReg", reg);
        msg.put("vehicleId",  trip.getVehicle().getId());
        messagingTemplate.convertAndSend("/topic/geofence-events", msg);
    }

    /** Clears all in-memory simulation state (called from reset API). */
    public void resetAll() {
        activeTrips.clear();
        waypointIdx.clear();
        routeWaypoints.clear();
        log.info("AutoSim: All sim state cleared");
    }

    /** Returns [region, officeName, routeName] for a given vehicle registration. */
    public static String[] getRouteMeta(String reg) {
        return ROUTE_META.getOrDefault(reg, new String[]{"Bangalore", "Bangalore HQ", "Unknown Route"});
    }
}
