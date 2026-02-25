package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;

/** Entity representing a circular office geofence (center + radius). */
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

}
