package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing the Office Geofence
 * Used to detect when a vehicle reaches the office location
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

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double radiusMeters; // Geofence radius in meters

}
