package com.moveinsync.vehicletracking.util;

/**
 * Utility class for geofence calculations
 * Uses Haversine formula to calculate distance between two GPS coordinates
 */
public class GeofenceUtil {

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

}
