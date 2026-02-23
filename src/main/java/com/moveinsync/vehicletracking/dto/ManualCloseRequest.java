package com.moveinsync.vehicletracking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO for EC4 — Manual Trip Closure request
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManualCloseRequest {

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    // Optional reason for manual closure — used in admin alert
    private String reason;
}
