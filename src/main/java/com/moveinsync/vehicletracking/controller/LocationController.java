package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.service.LocationAsyncService;
import com.moveinsync.vehicletracking.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for handling location update requests from vehicles
 */
@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
@Slf4j
public class LocationController {

    private final LocationService locationService;
    private final LocationAsyncService locationAsyncService;

    /** EC1 — enforce the maximum batch size declared in application.properties */
    @Value("${location.batch.max-size:100}")
    private int maxBatchSize;

    /**
     * Endpoint to receive location updates from vehicles
     * 
     * @param request Location update data with validation
     * @return Response indicating success or failure
     */
    @PostMapping("/update")
    public ResponseEntity<ApiResponse> updateLocation(@Valid @RequestBody LocationUpdateRequest request) {
        log.info("Received location update request for vehicle: {}, trip: {}", 
                request.getVehicleId(), request.getTripId());

        try {
            // Process the location update
            locationService.processLocationUpdate(request);

            // Return success response
            ApiResponse response = ApiResponse.builder()
                    .success(true)
                    .message("Location update processed successfully")
                    .data(null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing location update", e);
            throw e; // Let GlobalExceptionHandler handle it
        }
    }

    /**
     * EC1 — Network Fluctuation / Batch Sync
     * Accepts a list of buffered GPS pings from a device that was offline.
     * Pings are sorted by timestamp and processed chronologically.
     * Individual failures don't abort the batch — partial recovery is supported.
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse> batchUpdate(
            @RequestBody List<@Valid LocationUpdateRequest> requests) {
        log.info("EC1: Batch location update received — {} pings", requests.size());

        // EC1 — Guard: reject empty batches
        if (requests.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.builder().success(false)
                            .message("Batch is empty — nothing to process").build());
        }

        // EC1 — Guard: enforce max batch size to prevent memory/timeout abuse
        if (requests.size() > maxBatchSize) {
            log.warn("EC1: Batch rejected — size {} exceeds max allowed {}", requests.size(), maxBatchSize);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                    ApiResponse.builder().success(false)
                            .message("Batch size " + requests.size() +
                                    " exceeds maximum allowed " + maxBatchSize +
                                    ". Split into smaller batches.").build());
        }

        Map<String, Object> result = locationAsyncService.processBatch(requests);
        return ResponseEntity.ok(
                ApiResponse.builder().success(true)
                        .message("Batch processed: " + result.get("processed") + "/" + result.get("total"))
                        .data(result)
                        .build());
    }

    /**
     * EC8/EC9 — High Load / Async Processing
     * Returns 202 Accepted immediately; location is processed in background thread pool.
     * Use this endpoint for high-frequency streaming from many vehicles simultaneously.
     */
    @PostMapping("/update/async")
    public ResponseEntity<ApiResponse> updateLocationAsync(
            @Valid @RequestBody LocationUpdateRequest request) {
        log.info("EC8: Async location update queued — vehicle: {}, trip: {}",
                request.getVehicleId(), request.getTripId());
        locationAsyncService.processAsync(request);
        return ResponseEntity.accepted()
                .body(ApiResponse.builder().success(true)
                        .message("Location update accepted for async processing").build());
    }

}
