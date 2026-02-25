package com.moveinsync.vehicletracking.util;

/** Utility class for circular geofence (Haversine) calculations. */
public class GeofenceUtil {

    // Earth's radius in meters
    private static final double EARTH_RADIUS_METERS = 6371000;

    /** Returns the Haversine distance in metres between two GPS coordinates. */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1), lat2Rad = Math.toRadians(lat2);
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                 + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                 * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Returns true if the point (lat1, lon1) is within radiusMeters of (lat2, lon2). */
    public static boolean isWithinRadius(double lat1, double lon1, double lat2, double lon2, double radiusMeters) {
        return calculateDistance(lat1, lon1, lat2, lon2) <= radiusMeters;
    }

}

