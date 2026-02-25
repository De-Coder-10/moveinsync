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
 * REST API for managing circular office geofence configurations.
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

    @PostMapping
    public ResponseEntity<ApiResponse> create(
            @RequestBody @Valid OfficeGeofenceRequest request) {

        OfficeGeofence geofence = OfficeGeofence.builder()
                .name(request.getName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusMeters(request.getRadiusMeters())
                .build();

        OfficeGeofence saved = officeGeofenceRepository.save(geofence);
        log.info("Office geofence created: id={}, name='{}', radius={}m",
                saved.getId(), saved.getName(), saved.getRadiusMeters());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Geofence created successfully"));
    }

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

                    OfficeGeofence updated = officeGeofenceRepository.save(existing);
                    log.info("Office geofence updated: id={}, name='{}'",
                            updated.getId(), updated.getName());

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
