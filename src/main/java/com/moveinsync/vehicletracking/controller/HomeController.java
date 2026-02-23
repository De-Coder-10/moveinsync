package com.moveinsync.vehicletracking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Home controller that shows API info at the root URL
 */
@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "Vehicle Tracking & Geofence System");
        info.put("status", "Running");
        info.put("endpoints", Map.of(
                "POST /api/location/update", "Send vehicle GPS location update",
                "GET /", "This page",
                "GET /h2-console", "H2 Database Console"
        ));
        info.put("sampleRequest", Map.of(
                "url", "POST /api/location/update",
                "body", Map.of(
                        "vehicleId", 1,
                        "tripId", 1,
                        "latitude", 12.9520,
                        "longitude", 77.5750,
                        "speed", 30.0,
                        "timestamp", "2026-02-23T10:30:00"
                )
        ));
        return info;
    }

}
