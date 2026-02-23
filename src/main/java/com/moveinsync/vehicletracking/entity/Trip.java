package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Entity representing a Trip
 * A trip is associated with a vehicle and tracks the journey
 */
@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private String status; // CREATED, IN_PROGRESS, COMPLETED

    @Column(nullable = true)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    // Total distance driven in km (incremented on every GPS ping)
    private Double totalDistanceKm;

    // Duration of trip in minutes (set on trip completion)
    private Integer durationMinutes;

    // EC3 — Dwell time: records when vehicle first entered office geofence
    // Reset to null if vehicle exits (prevents GPS drift false trigger - EC2)
    private LocalDateTime officeEntryTime;

}
