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

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

}
