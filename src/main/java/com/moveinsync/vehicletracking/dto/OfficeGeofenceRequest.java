package com.moveinsync.vehicletracking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

/**
 * DTO for creating or updating an Office Geofence.
 *
 * Supports two geofence types:
 *   CIRCULAR — requires latitude, longitude, radiusMeters
 *   POLYGON  — requires polygonCoordinates (JSON array of [lat,lon] pairs);
 *              latitude/longitude/radiusMeters are stored but only used as a
 *              visual centroid / fallback reference.
 *
 * Example polygon request body:
 * {
 *   "name": "Pune Campus",
 *   "latitude": 18.5204,
 *   "longitude": 73.8567,
 *   "radiusMeters": 200,
 *   "geofenceType": "POLYGON",
 *   "polygonCoordinates": "[[18.519,73.855],[18.521,73.855],[18.521,73.858],[18.519,73.858]]"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeGeofenceRequest {

    /** Human-readable label for this geofence (e.g. "Bangalore HQ") */
    private String name;

    @NotNull(message = "latitude is required")
    private Double latitude;

    @NotNull(message = "longitude is required")
    private Double longitude;

    @NotNull(message = "radiusMeters is required")
    @Positive(message = "radiusMeters must be positive")
    private Double radiusMeters;

    /**
     * "CIRCULAR" (default) or "POLYGON".
     * Case-insensitive. Defaults to "CIRCULAR" if omitted.
     */
    @Builder.Default
    private String geofenceType = "CIRCULAR";

    /**
     * JSON array of [lat, lon] pairs.
     * Required when geofenceType = "POLYGON". Ignored for CIRCULAR.
     * Minimum 3 vertices to form a valid polygon.
     */
    private String polygonCoordinates;
}
