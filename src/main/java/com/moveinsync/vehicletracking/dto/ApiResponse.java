package com.moveinsync.vehicletracking.dto;

import lombok.*;

/**
 * DTO for API responses
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse {

    private boolean success;
    private String message;
    private Object data;

}
