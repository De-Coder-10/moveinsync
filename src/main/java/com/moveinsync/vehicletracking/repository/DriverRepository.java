package com.moveinsync.vehicletracking.repository;

import com.moveinsync.vehicletracking.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByVehicleId(Long vehicleId);
}
