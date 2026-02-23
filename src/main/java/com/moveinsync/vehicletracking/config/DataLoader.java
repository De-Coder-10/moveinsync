package com.moveinsync.vehicletracking.config;

import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Data loader that runs on application startup
 * Inserts sample data for testing the system
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final PickupPointRepository pickupPointRepository;
    private final OfficeGeofenceRepository officeGeofenceRepository;
    private final DriverRepository driverRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting data initialization...");

        // Check if data already exists to avoid duplicates
        if (vehicleRepository.count() > 0) {
            log.info("Data already exists, skipping initialization");
            return;
        }

        // Create Office Geofence
        // Using coordinates near Bangalore MG Road (example)
        OfficeGeofence officeGeofence = OfficeGeofence.builder()
                .latitude(12.9716)  // Bangalore MG Road area
                .longitude(77.5946)
                .radiusMeters(100.0) // 100 meters radius
                .build();
        officeGeofenceRepository.save(officeGeofence);
        log.info("Office geofence created: Lat={}, Lon={}, Radius={}m", 
                officeGeofence.getLatitude(), officeGeofence.getLongitude(), officeGeofence.getRadiusMeters());

        // Create Vehicle
        Vehicle vehicle = Vehicle.builder()
                .registrationNumber("KA01AB1234")
                .status("ACTIVE")
                .build();
        vehicleRepository.save(vehicle);
        log.info("Vehicle created: {}", vehicle.getRegistrationNumber());

        // Create Driver assigned to this vehicle
        Driver driver = Driver.builder()
                .name("Ravi Kumar")
                .phoneNumber("+91-9876543210")
                .licenseNumber("KA0120210012345")
                .vehicle(vehicle)
                .build();
        driverRepository.save(driver);
        log.info("Driver created: {}, Phone: {}", driver.getName(), driver.getPhoneNumber());

        // Create Trip with status IN_PROGRESS
        Trip trip = Trip.builder()
                .vehicle(vehicle)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now().minusHours(1)) // Started 1 hour ago
                .build();
        tripRepository.save(trip);
        log.info("Trip created: ID={}, Status={}", trip.getId(), trip.getStatus());

        // Create Pickup Point
        // Using coordinates 2km away from office (example location)
        PickupPoint pickupPoint = PickupPoint.builder()
                .trip(trip)
                .latitude(12.9520)  // About 2km from office
                .longitude(77.5750)
                .radiusMeters(50.0) // 50 meters radius
                .status("PENDING")
                .build();
        pickupPointRepository.save(pickupPoint);
        log.info("Pickup point created: Lat={}, Lon={}, Radius={}m, Status={}", 
                pickupPoint.getLatitude(), pickupPoint.getLongitude(), 
                pickupPoint.getRadiusMeters(), pickupPoint.getStatus());

        log.info("Data initialization completed successfully!");
        log.info("========================================");
        log.info("Sample Data Summary:");
        log.info("Vehicle ID: {}, Registration: {}", vehicle.getId(), vehicle.getRegistrationNumber());
        log.info("Trip ID: {}, Status: {}", trip.getId(), trip.getStatus());
        log.info("Pickup Point: Lat={}, Lon={}", pickupPoint.getLatitude(), pickupPoint.getLongitude());
        log.info("Office: Lat={}, Lon={}", officeGeofence.getLatitude(), officeGeofence.getLongitude());
        log.info("========================================");
    }

}
