package com.moveinsync.vehicletracking.dto;

import lombok.*;

// DATA TRANSER OBJECT (DTO)

/**
 * Generic API response wrapper.
 *
 * Provides Lombok @Builder for fine-grained construction AND
 * two static factory helpers for the most common cases:
 *   ApiResponse.success(data, message)
 *   ApiResponse.error(message)
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

    /** Shorthand for a successful response with data. */
    public static ApiResponse success(Object data, String message) {
        ApiResponse r = new ApiResponse();
        r.setSuccess(true);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    /** Shorthand for a successful response with no data payload. */
    public static ApiResponse success(String message) {
        ApiResponse r = new ApiResponse();
        r.setSuccess(true);
        r.setMessage(message);
        return r;
    }

    /** Shorthand for an error response. */
    public static ApiResponse error(String message) {
        ApiResponse r = new ApiResponse();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }

}
