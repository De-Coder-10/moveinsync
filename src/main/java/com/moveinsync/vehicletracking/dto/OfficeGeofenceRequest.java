package com.moveinsync.vehicletracking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

/** DTO for creating or updating a circular office geofence. */
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
}
