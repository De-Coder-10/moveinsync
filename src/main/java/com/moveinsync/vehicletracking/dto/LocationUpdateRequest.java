package com.moveinsync.vehicletracking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO for receiving location updates from vehicles
 * Contains validation annotations to ensure data integrity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationUpdateRequest {

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotNull(message = "Trip ID is required")
    private Long tripId;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    @NotNull(message = "Speed is required")
    private Double speed; // Speed in km/h

    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;

}
