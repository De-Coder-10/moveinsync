package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a Driver assigned to a Vehicle
 * Stores driver details displayed on the real-time tracking dashboard
 */
@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phoneNumber;

    private String licenseNumber;

    @OneToOne
    @JoinColumn(name = "vehicle_id", unique = true)
    private Vehicle vehicle;
}
