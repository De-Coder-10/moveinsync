package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * EC1  — Network Fluctuation: Batch sync for buffered GPS data
 * EC8  — High Load: Async processing via dedicated thread pool
 * EC9  — Scalability: Non-blocking GPS updates, CallerRunsPolicy backpressure
 *
 * Why a separate service?
 *  LocationService.processLocationUpdate is @Transactional.
 *  Calling it from the same bean bypasses Spring's proxy → transactions don't work.
 *  Calling it from THIS bean goes through the proxy → each ping gets its own transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationAsyncService {

    private final LocationService locationService;

    /**
     * EC8/EC9 — Async single ping processing.
     * GPS device gets 202 Accepted immediately; processing happens in background thread.
     * Uses "locationTaskExecutor" pool (10 core / 50 max / 500 queue).
     */
    @Async("locationTaskExecutor")
    public CompletableFuture<Void> processAsync(LocationUpdateRequest request) {
        try {
            locationService.processLocationUpdate(request);
        } catch (Exception e) {
            log.error("EC8: Async location processing failed — vehicle: {}, trip: {} — {}",
                    request.getVehicleId(), request.getTripId(), e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * EC1 — Batch sync for buffered GPS data after network reconnection.
     *
     * Steps:
     * 1. Sort all buffered pings by timestamp (handles out-of-order delivery)
     * 2. Process each ping sequentially through LocationService (each in own transaction)
     * 3. Failed pings are logged but don't abort the rest (partial recovery)
     *
     * This ensures: route trail is complete, audit log is intact, trip events fire correctly.
     */
    public Map<String, Object> processBatch(List<LocationUpdateRequest> requests) {
        log.info("EC1: Batch sync started — {} buffered pings", requests.size());

        // Sort by timestamp for correct chronological processing
        List<LocationUpdateRequest> sorted = requests.stream()
                .sorted(Comparator.comparing(LocationUpdateRequest::getTimestamp))
                .toList();

        int processed = 0, failed = 0;
        for (LocationUpdateRequest req : sorted) {
            try {
                locationService.processLocationUpdate(req); // each call is its own @Transactional
                processed++;
            } catch (Exception e) {
                log.error("EC1: Batch ping failed — vehicle: {}, trip: {}, ts: {} — {}",
                        req.getVehicleId(), req.getTripId(), req.getTimestamp(), e.getMessage());
                failed++;
            }
        }

        log.info("EC1: Batch sync complete — total: {}, processed: {}, failed: {}",
                requests.size(), processed, failed);
        return Map.of("total", requests.size(), "processed", processed, "failed", failed);
    }
}
