package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a Vehicle in the system
 */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    @Column(nullable = false)
    private String status; // e.g., ACTIVE, INACTIVE

}
