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
 * REST API for the single office geofence configuration.
 *
 * Endpoints:
 *   GET    /api/geofences  — get the configured geofence
 *   POST   /api/geofences  — create the geofence
 *   PUT    /api/geofences  — update the geofence
 *   DELETE /api/geofences  — remove the geofence
 */
@RestController
@RequestMapping("/api/geofences")
@RequiredArgsConstructor
@Slf4j
public class OfficeGeofenceController {

    private final OfficeGeofenceRepository officeGeofenceRepository;

    @GetMapping
    public ResponseEntity<ApiResponse> get() {
        List<OfficeGeofence> geofences = officeGeofenceRepository.findAll();
        if (geofences.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No geofence configured"));
        return ResponseEntity.ok(ApiResponse.success(geofences.get(0), "Geofence found"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> create(@RequestBody @Valid OfficeGeofenceRequest request) {
        OfficeGeofence geofence = OfficeGeofence.builder()
                .name(request.getName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusMeters(request.getRadiusMeters())
                .build();
        OfficeGeofence saved = officeGeofenceRepository.save(geofence);
        log.info("Office geofence created: id={}, name='{}', radius={}m",
                saved.getId(), saved.getName(), saved.getRadiusMeters());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(saved, "Geofence created successfully"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse> update(@RequestBody @Valid OfficeGeofenceRequest request) {
        List<OfficeGeofence> geofences = officeGeofenceRepository.findAll();
        if (geofences.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No geofence configured"));
        OfficeGeofence existing = geofences.get(0);
        existing.setName(request.getName());
        existing.setLatitude(request.getLatitude());
        existing.setLongitude(request.getLongitude());
        existing.setRadiusMeters(request.getRadiusMeters());
        OfficeGeofence updated = officeGeofenceRepository.save(existing);
        log.info("Office geofence updated: id={}, name='{}'", updated.getId(), updated.getName());
        return ResponseEntity.ok(ApiResponse.success(updated, "Geofence updated successfully"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> delete() {
        List<OfficeGeofence> geofences = officeGeofenceRepository.findAll();
        if (geofences.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No geofence configured"));
        officeGeofenceRepository.delete(geofences.get(0));
        log.info("Office geofence deleted");
        return ResponseEntity.ok(ApiResponse.success(null, "Geofence deleted successfully"));
    }
}
