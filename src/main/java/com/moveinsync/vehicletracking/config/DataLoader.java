package com.moveinsync.vehicletracking.config;

import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;


//   Inserts sample data for testing the system
 
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
        OfficeGeofence officeGeofence = OfficeGeofence.builder()
                .name("Bangalore HQ")
                .latitude(12.9716)  // Bangalore MG Road area
                .longitude(77.5946)
                .radiusMeters(100.0) // 100 metres radius
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
                .name("Vighnes Bajpai")
                .phoneNumber("+91-9876543210")
                .licenseNumber("KA0120210012345")
                .vehicle(vehicle)
                .build();
        driverRepository.save(driver);
        log.info("Driver created: {}, Phone: {}", driver.getName(), driver.getPhoneNumber());

        // Create Trip with status PENDING (trips must be started manually)
        Trip trip = Trip.builder()
                .vehicle(vehicle)
                .status("PENDING")
                .build();
        tripRepository.save(trip);
        log.info("Trip created: ID={}, Status={}", trip.getId(), trip.getStatus());

        // Create Pickup Point

        PickupPoint pickupPoint = PickupPoint.builder()
                .trip(trip)
                .latitude(12.9520)  // About 2km from office
                .longitude(77.5750)
                .radiusMeters(50.0) 
                .status("PENDING")
                .build();
        pickupPointRepository.save(pickupPoint);
        log.info("Pickup point created: Lat={}, Lon={}, Radius={}m, Status={}", 
                pickupPoint.getLatitude(), pickupPoint.getLongitude(), 
                pickupPoint.getRadiusMeters(), pickupPoint.getStatus());

        // ─── Vehicle 3 — East Route (HAL/Indiranagar → MG Road) ─────────────
        Vehicle vehicle3 = Vehicle.builder()
                .registrationNumber("TN03EF9012")
                .status("ACTIVE")
                .build();
        vehicleRepository.save(vehicle3);

        Driver driver3 = Driver.builder()
                .name("Krishna Seth")
                .phoneNumber("+91-9988776655")
                .licenseNumber("TN0320200098765")
                .vehicle(vehicle3)
                .build();
        driverRepository.save(driver3);

        Trip trip3 = Trip.builder()
                .vehicle(vehicle3)
                .status("PENDING")
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

        log.info("Data initialization completed — 2 vehicles, 2 trips, 2 pickup points");
    }

}
