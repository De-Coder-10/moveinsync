package com.moveinsync.vehicletracking.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for geofence calculations.
 *
 * Supports two geofence shapes:
 *  - Circular : Haversine distance check against a center + radius
 *  - Polygon  : Ray-casting algorithm against an arbitrary polygon boundary
 *               (suitable for complex office campuses / irregular shapes)
 */
public class GeofenceUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Earth's radius in meters
    private static final double EARTH_RADIUS_METERS = 6371000;

    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Convert degrees to radians
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        // Calculate differences
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Distance in meters
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Check if a point is within a geofence radius
     * 
     * @param lat1 Latitude of point to check
     * @param lon1 Longitude of point to check
     * @param lat2 Latitude of geofence center
     * @param lon2 Longitude of geofence center
     * @param radiusMeters Radius of geofence in meters
     * @return true if point is within radius, false otherwise
     */
    public static boolean isWithinRadius(double lat1, double lon1, double lat2, double lon2, double radiusMeters) {
        double distance = calculateDistance(lat1, lon1, lat2, lon2);
        return distance <= radiusMeters;
    }

    /**
     * Check if a point is inside a polygon using the Ray-Casting algorithm.
     *
     * The polygon is provided as a JSON array of [lat, lon] pairs:
     *   e.g. [[12.970,77.593],[12.972,77.593],[12.972,77.596],[12.970,77.596]]
     *
     * Algorithm: Cast a horizontal ray from the test point eastward and count
     * how many polygon edges it crosses. An odd count means the point is inside.
     *
     * @param lat                   latitude of the point to test
     * @param lon                   longitude of the point to test
     * @param polygonCoordinatesJson JSON array of [lat,lon] pairs (minimum 3 vertices)
     * @return true if the point is inside the polygon, false otherwise
     */
    public static boolean isWithinPolygon(double lat, double lon, String polygonCoordinatesJson) {
        if (polygonCoordinatesJson == null || polygonCoordinatesJson.isBlank()) return false;
        try {
            double[][] polygon = OBJECT_MAPPER.readValue(polygonCoordinatesJson, double[][].class);
            if (polygon.length < 3) return false;
            return raycastInside(lat, lon, polygon);
        } catch (Exception e) {
            return false; // malformed JSON — treat as outside
        }
    }

    /**
     * Ray-casting inside test for a 2-D polygon.
     *
     * @param lat     latitude of the test point (treated as X-axis)
     * @param lon     longitude of the test point (treated as Y-axis)
     * @param polygon array of [lat, lon] pairs
     * @return true if the point is inside the polygon
     */
    private static boolean raycastInside(double lat, double lon, double[][] polygon) {
        int n = polygon.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = polygon[i][0], lonI = polygon[i][1];
            double latJ = polygon[j][0], lonJ = polygon[j][1];
            // Does the edge cross the horizontal line through (lat, lon)?
            if (((lonI > lon) != (lonJ > lon)) &&
                    (lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI)) {
                inside = !inside;
            }
        }
        return inside;
    }

}
