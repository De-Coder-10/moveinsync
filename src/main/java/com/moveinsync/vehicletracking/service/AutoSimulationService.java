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
            "MH02CD5678", new double[]{12.9050, 77.6020},
            "TN03EF9012", new double[]{12.9780, 77.6450}
    );

    /** All routes end at Bangalore HQ */
    private static final double[] OFFICE = {12.9716, 77.5946};

    /** Fallback straight-line routes used when OSRM is unreachable */
    private static final Map<String, double[][]> FALLBACK_ROUTES = new LinkedHashMap<>();

    static {
        FALLBACK_ROUTES.put("KA01AB1234", new double[][]{
            {12.9520,77.5750},{12.9528,77.5758},{12.9536,77.5766},{12.9543,77.5774},
            {12.9551,77.5782},{12.9559,77.5789},{12.9567,77.5797},{12.9575,77.5805},
            {12.9583,77.5813},{12.9590,77.5821},{12.9598,77.5829},{12.9606,77.5837},
            {12.9614,77.5844},{12.9622,77.5852},{12.9630,77.5860},{12.9637,77.5868},
            {12.9645,77.5876},{12.9653,77.5884},{12.9661,77.5892},{12.9669,77.5899},
            {12.9676,77.5907},{12.9684,77.5915},{12.9692,77.5923},{12.9700,77.5931},
            {12.9708,77.5939},{12.9716,77.5946}
        });
        FALLBACK_ROUTES.put("MH02CD5678", new double[][]{
            {12.9050,77.6020},{12.9077,77.6017},{12.9103,77.6014},{12.9130,77.6011},
            {12.9157,77.6008},{12.9183,77.6005},{12.9210,77.6003},{12.9237,77.6000},
            {12.9264,77.5997},{12.9290,77.5994},{12.9317,77.5991},{12.9344,77.5988},
            {12.9370,77.5985},{12.9397,77.5982},{12.9424,77.5979},{12.9450,77.5976},
            {12.9477,77.5973},{12.9504,77.5970},{12.9530,77.5967},{12.9557,77.5964},
            {12.9584,77.5961},{12.9610,77.5958},{12.9637,77.5956},{12.9664,77.5953},
            {12.9690,77.5950},{12.9716,77.5946}
        });
        FALLBACK_ROUTES.put("TN03EF9012", new double[][]{
            {12.9780,77.6450},{12.9777,77.6429},{12.9775,77.6409},{12.9772,77.6389},
            {12.9769,77.6369},{12.9767,77.6349},{12.9764,77.6328},{12.9762,77.6308},
            {12.9759,77.6288},{12.9756,77.6268},{12.9754,77.6247},{12.9751,77.6227},
            {12.9748,77.6207},{12.9746,77.6187},{12.9743,77.6167},{12.9741,77.6146},
            {12.9738,77.6126},{12.9735,77.6106},{12.9733,77.6086},{12.9730,77.6065},
            {12.9728,77.6045},{12.9725,77.6025},{12.9722,77.6005},{12.9720,77.5985},
            {12.9717,77.5964},{12.9716,77.5946}
        });
    }

    private static final Map<String, String[]> ROUTE_META = Map.of(
            "KA01AB1234", new String[]{"Bangalore", "Bangalore HQ", "North Route"},
            "MH02CD5678", new String[]{"Pune",      "Bangalore HQ", "South Route"},
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
            log.warn("AutoSim: OSRM unavailable for {} -- using fallback: {}", reg, e.getMessage());
            return FALLBACK_ROUTES.getOrDefault(reg, FALLBACK_ROUTES.get("KA01AB1234"));
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
