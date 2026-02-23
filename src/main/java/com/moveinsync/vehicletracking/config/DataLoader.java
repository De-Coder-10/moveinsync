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
                .name("Bangalore HQ")
                .latitude(12.9716)  // Bangalore MG Road area
                .longitude(77.5946)
                .radiusMeters(100.0) // 100 metres radius
                .geofenceType("CIRCULAR")
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

        // ─── Vehicle 2 — South Route (HSR Layout → MG Road) ──────────────────
        Vehicle vehicle2 = Vehicle.builder()
                .registrationNumber("MH02CD5678")
                .status("ACTIVE")
                .build();
        vehicleRepository.save(vehicle2);

        Driver driver2 = Driver.builder()
                .name("Arjun Singh")
                .phoneNumber("+91-9123456789")
                .licenseNumber("MH0220220056789")
                .vehicle(vehicle2)
                .build();
        driverRepository.save(driver2);

        Trip trip2 = Trip.builder()
                .vehicle(vehicle2)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now().minusMinutes(20))
                .build();
        tripRepository.save(trip2);

        PickupPoint pickup2 = PickupPoint.builder()
                .trip(trip2)
                .latitude(12.9050)
                .longitude(77.6020)
                .radiusMeters(50.0)
                .status("PENDING")
                .build();
        pickupPointRepository.save(pickup2);
        log.info("Vehicle 2 created: {} | Trip: #{} | Pickup: ({},{})",
                vehicle2.getRegistrationNumber(), trip2.getId(),
                pickup2.getLatitude(), pickup2.getLongitude());

        // ─── Vehicle 3 — East Route (HAL/Indiranagar → MG Road) ─────────────
        Vehicle vehicle3 = Vehicle.builder()
                .registrationNumber("TN03EF9012")
                .status("ACTIVE")
                .build();
        vehicleRepository.save(vehicle3);

        Driver driver3 = Driver.builder()
                .name("Priya Sharma")
                .phoneNumber("+91-9988776655")
                .licenseNumber("TN0320200098765")
                .vehicle(vehicle3)
                .build();
        driverRepository.save(driver3);

        Trip trip3 = Trip.builder()
                .vehicle(vehicle3)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now().minusMinutes(35))
                .build();
        tripRepository.save(trip3);

        PickupPoint pickup3 = PickupPoint.builder()
                .trip(trip3)
                .latitude(12.9780)
                .longitude(77.6450)
                .radiusMeters(50.0)
                .status("PENDING")
                .build();
        pickupPointRepository.save(pickup3);
        log.info("Vehicle 3 created: {} | Trip: #{} | Pickup: ({},{})",
                vehicle3.getRegistrationNumber(), trip3.getId(),
                pickup3.getLatitude(), pickup3.getLongitude());

        log.info("Data initialization completed — 3 vehicles, 3 trips, 3 pickup points");
    }

}
