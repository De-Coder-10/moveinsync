package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ApiResponse;
import com.moveinsync.vehicletracking.dto.OfficeGeofenceRequest;
import com.moveinsync.vehicletracking.entity.OfficeGeofence;
import com.moveinsync.vehicletracking.repository.OfficeGeofenceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing Office Geofence configurations.
 *
 * Allows enterprise admins to configure office locations without redeploying
 * the application. Supports both CIRCULAR and POLYGON geofence types.
 *
 * Endpoints:
 *   GET    /api/geofences           — list all configured geofences
 *   GET    /api/geofences/{id}      — get a specific geofence by ID
 *   POST   /api/geofences           — create a new geofence
 *   PUT    /api/geofences/{id}      — update an existing geofence
 *   DELETE /api/geofences/{id}      — remove a geofence
 */
@RestController
@RequestMapping("/api/geofences")
@RequiredArgsConstructor
@Slf4j
public class OfficeGeofenceController {

    private final OfficeGeofenceRepository officeGeofenceRepository;

    /**
     * List all configured office geofences.
     * Used by the dashboard and admin UI to display current geofence configs.
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getAll() {
        List<OfficeGeofence> geofences = officeGeofenceRepository.findAll();
        log.debug("Returning {} office geofences", geofences.size());
        return ResponseEntity.ok(ApiResponse.success(
                geofences, geofences.size() + " geofence(s) found"));
    }

    /**
     * Get a single geofence by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getById(@PathVariable Long id) {
        return officeGeofenceRepository.findById(id)
                .map(g -> ResponseEntity.ok(ApiResponse.success(g, "Geofence found")))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Geofence not found with ID: " + id)));
    }

    /**
     * Create a new office geofence.
     *
     * For CIRCULAR type, only latitude/longitude/radiusMeters are needed.
     * For POLYGON type, polygonCoordinates must be a valid JSON array of [lat,lon] pairs.
     *
     * Example: POST /api/geofences
     * {
     *   "name": "Bangalore HQ",
     *   "latitude": 12.9716,
     *   "longitude": 77.5946,
     *   "radiusMeters": 100,
     *   "geofenceType": "CIRCULAR"
     * }
     */
    @PostMapping
    public ResponseEntity<ApiResponse> create(
            @RequestBody @Valid OfficeGeofenceRequest request) {

        // Validate polygon coordinates if type is POLYGON
        if ("POLYGON".equalsIgnoreCase(request.getGeofenceType())) {
            if (request.getPolygonCoordinates() == null || request.getPolygonCoordinates().isBlank()) {
                return ResponseEntity
                        .badRequest()
                        .body(ApiResponse.error("polygonCoordinates is required for POLYGON geofence type"));
            }
        }

        OfficeGeofence geofence = OfficeGeofence.builder()
                .name(request.getName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusMeters(request.getRadiusMeters())
                .geofenceType(request.getGeofenceType() != null
                        ? request.getGeofenceType().toUpperCase() : "CIRCULAR")
                .polygonCoordinates(request.getPolygonCoordinates())
                .build();

        OfficeGeofence saved = officeGeofenceRepository.save(geofence);
        log.info("Office geofence created: id={}, name='{}', type={}, radius={}m",
                saved.getId(), saved.getName(), saved.getGeofenceType(), saved.getRadiusMeters());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Geofence created successfully"));
    }

    /**
     * Update an existing office geofence by ID.
     * Useful for adjusting the radius or changing between CIRCULAR and POLYGON.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid OfficeGeofenceRequest request) {

        return officeGeofenceRepository.findById(id)
                .map(existing -> {
                    existing.setName(request.getName());
                    existing.setLatitude(request.getLatitude());
                    existing.setLongitude(request.getLongitude());
                    existing.setRadiusMeters(request.getRadiusMeters());
                    existing.setGeofenceType(request.getGeofenceType() != null
                            ? request.getGeofenceType().toUpperCase() : "CIRCULAR");
                    existing.setPolygonCoordinates(request.getPolygonCoordinates());

                    OfficeGeofence updated = officeGeofenceRepository.save(existing);
                    log.info("Office geofence updated: id={}, name='{}', type={}",
                            updated.getId(), updated.getName(), updated.getGeofenceType());

                    return ResponseEntity.ok(ApiResponse.success(updated, "Geofence updated successfully"));
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Geofence not found with ID: " + id)));
    }

    /**
     * Delete an office geofence by ID.
     * Warning: deleting the only geofence will disable auto trip closure.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> delete(@PathVariable Long id) {
        if (!officeGeofenceRepository.existsById(id)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Geofence not found with ID: " + id));
        }

        officeGeofenceRepository.deleteById(id);
        log.info("Office geofence deleted: id={}", id);
        return ResponseEntity.ok(ApiResponse.success(null, "Geofence deleted successfully"));
    }
}
