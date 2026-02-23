package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing an Office Geofence.
 *
 * Supports two geofence types:
 *  - CIRCULAR (default): defined by a center point (latitude, longitude) + radiusMeters
 *  - POLYGON: defined by polygonCoordinates — a JSON array of [lat,lon] pairs
 *             e.g. [[12.970,77.593],[12.972,77.593],[12.972,77.596],[12.970,77.596]]
 *
 * A name field is provided so enterprise clients can identify each campus geofence.
 */
@Entity
@Table(name = "office_geofences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfficeGeofence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable label, e.g. "Bangalore HQ", "Pune Campus" */
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double radiusMeters;

    /**
     * "CIRCULAR" (default) or "POLYGON".
     * If POLYGON, radiusMeters is ignored and polygonCoordinates is used.
     * Null is treated as CIRCULAR by the geofence engine.
     */
    @Column(nullable = false)
    private String geofenceType;

    /**
     * JSON array of [lat,lon] pairs defining a polygon boundary.
     * Used only when geofenceType = "POLYGON".
     * Example: [[12.970,77.593],[12.972,77.593],[12.972,77.596],[12.970,77.596]]
     */
    @Column(columnDefinition = "TEXT")
    private String polygonCoordinates;

}
