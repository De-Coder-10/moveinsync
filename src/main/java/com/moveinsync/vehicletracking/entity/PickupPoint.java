package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a Pickup Point for a trip
 * Contains geofence information for pickup detection
 */
@Entity
@Table(name = "pickup_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double radiusMeters; // Geofence radius in meters

    @Column(nullable = false)
    private String status; // PENDING, ARRIVED

}
