package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-drives all active vehicles along predefined routes every 1.5 seconds.
 *
 * - Each vehicle moves one waypoint per tick (pickup → office)
 * - Pickup geofence detection + office geofence closure are automatic
 *   (delegated to LocationService which already handles all edge cases)
 * - Completed trips auto-reset 8 s after completion to loop continuously
 * - Vehicles are staggered so they show at different positions on startup
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

    /** Current waypoint index per trip ID */
    private final ConcurrentHashMap<Long, Integer> waypointIdx = new ConcurrentHashMap<>();
    /** Epoch-ms at which a completed trip should be reset */
    private final ConcurrentHashMap<Long, Long> resetAtMs = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Route Definitions
    // All routes terminate at Bangalore HQ: (12.9716, 77.5946)
    // Waypoints are straight-line interpolated (26 points, index 0 = pickup)
    // ─────────────────────────────────────────────────────────────────────────
    private static final Map<String, double[][]> ROUTES = new LinkedHashMap<>();

    static {
        // KA01AB1234 — North Route: Koramangala (SW) → MG Road (NE)
        ROUTES.put("KA01AB1234", new double[][]{
            {12.9520,77.5750},{12.9528,77.5758},{12.9536,77.5766},{12.9543,77.5774},
            {12.9551,77.5782},{12.9559,77.5789},{12.9567,77.5797},{12.9575,77.5805},
            {12.9583,77.5813},{12.9590,77.5821},{12.9598,77.5829},{12.9606,77.5837},
            {12.9614,77.5844},{12.9622,77.5852},{12.9630,77.5860},{12.9637,77.5868},
            {12.9645,77.5876},{12.9653,77.5884},{12.9661,77.5892},{12.9669,77.5899},
            {12.9676,77.5907},{12.9684,77.5915},{12.9692,77.5923},{12.9700,77.5931},
            {12.9708,77.5939},{12.9716,77.5946}
        });

        // MH02CD5678 — South Route: HSR Layout (S) → MG Road (N)
        ROUTES.put("MH02CD5678", new double[][]{
            {12.9050,77.6020},{12.9077,77.6017},{12.9103,77.6014},{12.9130,77.6011},
            {12.9157,77.6008},{12.9183,77.6005},{12.9210,77.6003},{12.9237,77.6000},
            {12.9264,77.5997},{12.9290,77.5994},{12.9317,77.5991},{12.9344,77.5988},
            {12.9370,77.5985},{12.9397,77.5982},{12.9424,77.5979},{12.9450,77.5976},
            {12.9477,77.5973},{12.9504,77.5970},{12.9530,77.5967},{12.9557,77.5964},
            {12.9584,77.5961},{12.9610,77.5958},{12.9637,77.5956},{12.9664,77.5953},
            {12.9690,77.5950},{12.9716,77.5946}
        });

        // TN03EF9012 — East Route: HAL/Indiranagar (E) → MG Road (W)
        ROUTES.put("TN03EF9012", new double[][]{
            {12.9780,77.6450},{12.9777,77.6429},{12.9775,77.6409},{12.9772,77.6389},
            {12.9769,77.6369},{12.9767,77.6349},{12.9764,77.6328},{12.9762,77.6308},
            {12.9759,77.6288},{12.9756,77.6268},{12.9754,77.6247},{12.9751,77.6227},
            {12.9748,77.6207},{12.9746,77.6187},{12.9743,77.6167},{12.9741,77.6146},
            {12.9738,77.6126},{12.9735,77.6106},{12.9733,77.6086},{12.9730,77.6065},
            {12.9728,77.6045},{12.9725,77.6025},{12.9722,77.6005},{12.9720,77.5985},
            {12.9717,77.5964},{12.9716,77.5946}
        });
    }

    /**
     * Region / office / route labels per vehicle registration.
     * Used by DashboardApiController to populate filter metadata.
     */
    private static final Map<String, String[]> ROUTE_META = Map.of(
        "KA01AB1234", new String[]{"Bangalore", "Bangalore HQ", "North Route"},
        "MH02CD5678", new String[]{"Pune",      "Bangalore HQ", "South Route"},
        "TN03EF9012", new String[]{"Chennai",   "Bangalore HQ", "East Route"}
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduler tick — runs every 1.5 s
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 1500)
    @Transactional
    public void tick() {
        List<Trip> trips;
        try {
            trips = tripRepository.findAll();
        } catch (Exception e) {
            log.debug("AutoSim: DB not ready yet — {}", e.getMessage());
            return;
        }

        for (Trip trip : trips) {
            String reg = trip.getVehicle().getRegistrationNumber();
            if (!ROUTES.containsKey(reg)) continue;

            try {
                if ("IN_PROGRESS".equals(trip.getStatus())) {
                    advance(trip);
                } else if ("COMPLETED".equals(trip.getStatus())) {
                    maybeReset(trip);
                }
            } catch (Exception e) {
                log.warn("AutoSim tick error for trip #{}: {}", trip.getId(), e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Move vehicle one step along its route
    // ─────────────────────────────────────────────────────────────────────────
    private void advance(Trip trip) {
        String reg = trip.getVehicle().getRegistrationNumber();
        double[][] pts = ROUTES.get(reg);

        int idx = waypointIdx.computeIfAbsent(trip.getId(), k -> 0);
        // Clamp: stay at last waypoint so dwell-time can accumulate
        int clampedIdx = Math.min(idx, pts.length - 1);

        LocationUpdateRequest req = new LocationUpdateRequest();
        req.setVehicleId(trip.getVehicle().getId());
        req.setTripId(trip.getId());
        req.setLatitude(pts[clampedIdx][0]);
        req.setLongitude(pts[clampedIdx][1]);
        req.setSpeed(computeSpeed(clampedIdx, pts.length));
        req.setTimestamp(LocalDateTime.now());

        locationService.processLocationUpdate(req);

        // Advance only if not yet at last waypoint
        if (idx < pts.length - 1) {
            waypointIdx.put(trip.getId(), idx + 1);
        }
    }

    /**
     * Speed profile: slow at pickup (t≈0), cruise in middle, slow at office (t≈1).
     */
    private double computeSpeed(int idx, int total) {
        double t = (double) idx / Math.max(total - 1, 1);
        if      (t < 0.05) return 2.0;
        else if (t < 0.20) return 5.0 + t * 150;                      // 5 → 35 km/h ramp-up
        else if (t < 0.80) return 35.0 + Math.sin(t * Math.PI) * 8;  // 35 – 43 km/h cruise
        else if (t < 0.92) return 35.0 - (t - 0.80) / 0.12 * 30;    // 35 → 5 km/h ramp-down
        else               return 2.0;                                  // at office, stopped
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule + execute trip reset 8 s after completion
    // ─────────────────────────────────────────────────────────────────────────
    private void maybeReset(Trip trip) {
        long now = System.currentTimeMillis();
        long target = resetAtMs.computeIfAbsent(trip.getId(), k -> now + 8_000L);
        if (now >= target) {
            doReset(trip);
        }
    }

    @Transactional
    public void doReset(Trip trip) {
        Long id = trip.getId();
        String reg = trip.getVehicle().getRegistrationNumber();

        // Clear history
        locationLogRepository.deleteAll(locationLogRepository.findByTripIdOrderByTimestampAsc(id));
        eventLogRepository.deleteAll(eventLogRepository.findByTripId(id));

        // Reset trip
        trip.setStatus("IN_PROGRESS");
        trip.setStartTime(LocalDateTime.now());
        trip.setEndTime(null);
        trip.setTotalDistanceKm(null);
        trip.setDurationMinutes(null);
        trip.setOfficeEntryTime(null);
        tripRepository.save(trip);

        // Reset pickups
        pickupPointRepository.findAllByTripId(id)
            .forEach(pp -> { pp.setStatus("PENDING"); pickupPointRepository.save(pp); });

        // Reset sim state
        waypointIdx.put(id, 0);
        resetAtMs.remove(id);

        log.info("AutoSim: Trip #{} ({}) restarted from beginning", id, reg);

        // Notify frontend immediately
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("eventType", "TRIP_RESET");
        msg.put("tripId", id);
        msg.put("vehicleReg", reg);
        msg.put("vehicleId", trip.getVehicle().getId());
        messagingTemplate.convertAndSend("/topic/geofence-events", msg);
    }

    /**
     * Called by DashboardApiController.resetTrip() to also clear in-memory sim state.
     */
    public void resetAll() {
        waypointIdx.clear();
        resetAtMs.clear();
        log.info("AutoSim: All sim state cleared");
    }

    /**
     * Returns [region, officeName, routeName] for a given vehicle registration.
     * Defaults to Bangalore / Bangalore HQ / Unknown Route.
     */
    public static String[] getRouteMeta(String reg) {
        return ROUTE_META.getOrDefault(reg, new String[]{"Bangalore", "Bangalore HQ", "Unknown Route"});
    }
}
