package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for handling location update requests from vehicles
 */
@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
@Slf4j
public class LocationController {

    private final LocationService locationService;

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

}
