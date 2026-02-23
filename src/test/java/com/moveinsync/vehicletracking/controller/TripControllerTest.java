package com.moveinsync.vehicletracking.controller;

import com.moveinsync.vehicletracking.dto.ManualCloseRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import com.moveinsync.vehicletracking.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TripController — Audit & Compliance: EC4 Manual Closure.
 *
 * Test cases:
 *  1. manualCloseOutsideGeofence_logsDiscrepancyAndAdminAlert
 *     Driver closes trip OUTSIDE office geofence →
 *     MANUAL_CLOSURE_OUTSIDE_GEOFENCE + ADMIN_ALERT events persisted,
 *     admin notification fired.
 *
 *  2. manualCloseInsideGeofence_logsManuaClosureOnly
 *     Driver closes trip INSIDE office geofence →
 *     only MANUAL_CLOSURE event persisted, no admin alert.
 *
 *  3. manualCloseAlreadyCompleted_returnsBadRequest
 *     Trip already COMPLETED → reject with 400, no event saved.
 */
@ExtendWith(MockitoExtension.class)
class TripControllerTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    @Mock private TripRepository           tripRepository;
    @Mock private OfficeGeofenceRepository officeGeofenceRepository;
    @Mock private EventLogRepository       eventLogRepository;
    @Mock private NotificationService      notificationService;

    @InjectMocks
    private TripController tripController;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private static final Long VEHICLE_ID = 1L;
    private static final Long TRIP_ID    = 1L;

    /** Office geofence centre */
    private static final double OFFICE_LAT    = 12.9716;
    private static final double OFFICE_LON    = 77.5946;
    private static final double OFFICE_RADIUS = 100.0;

    /** A position well outside the office geofence (≈ 7km away) */
    private static final double OUTSIDE_LAT = 12.900;
    private static final double OUTSIDE_LON = 77.500;

    private Trip buildActiveTrip() {
        Vehicle vehicle = Vehicle.builder()
                .id(VEHICLE_ID)
                .registrationNumber("KA01AB1234")
                .status("ACTIVE")
                .build();
        return Trip.builder()
                .id(TRIP_ID)
                .vehicle(vehicle)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now().minusHours(1))
                .build();
    }

    private OfficeGeofence buildOfficeGeofence() {
        return OfficeGeofence.builder()
                .id(1L)
                .latitude(OFFICE_LAT)
                .longitude(OFFICE_LON)
                .radiusMeters(OFFICE_RADIUS)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Test 1 — Manual close OUTSIDE geofence: discrepancy + admin alert logged
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("EC4: Manual close outside geofence → MANUAL_CLOSURE_OUTSIDE_GEOFENCE + ADMIN_ALERT saved")
    void manualCloseOutsideGeofence_logsDiscrepancyAndAdminAlert() {
        // Given: trip in progress, vehicle outside office geofence
        Trip trip = buildActiveTrip();
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(officeGeofenceRepository.findAll()).thenReturn(List.of(buildOfficeGeofence()));
        when(eventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManualCloseRequest req = new ManualCloseRequest(OUTSIDE_LAT, OUTSIDE_LON, "Driver ended shift early");

        // When
        ResponseEntity<?> response = tripController.manualClose(TRIP_ID, req);

        // Then: HTTP 200 OK
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        // Capture all saved events
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(2)).save(captor.capture());

        List<GeofenceEventType> savedTypes = captor.getAllValues().stream()
                .map(EventLog::getEventType)
                .toList();

        // Both compliance events must be saved
        assertThat(savedTypes).containsExactlyInAnyOrder(
                GeofenceEventType.MANUAL_CLOSURE_OUTSIDE_GEOFENCE,
                GeofenceEventType.ADMIN_ALERT);

        // All events must carry vehicle + trip IDs
        captor.getAllValues().forEach(e -> {
            assertThat(e.getVehicleId()).isEqualTo(VEHICLE_ID);
            assertThat(e.getTripId()).isEqualTo(TRIP_ID);
            assertThat(e.getTimestamp()).isNotNull();
        });

        // Admin notification must fire
        verify(notificationService).sendAdminAlert(eq(VEHICLE_ID), eq(TRIP_ID), anyString());

        // Trip must be marked COMPLETED regardless
        assertThat(trip.getStatus()).isEqualTo("COMPLETED");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Test 2 — Manual close INSIDE geofence: only MANUAL_CLOSURE logged
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Manual close inside geofence → only MANUAL_CLOSURE event saved, no admin alert")
    void manualCloseInsideGeofence_logsManualClosureOnly() {
        // Given: vehicle exactly at office location (inside geofence)
        Trip trip = buildActiveTrip();
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(officeGeofenceRepository.findAll()).thenReturn(List.of(buildOfficeGeofence()));
        when(eventLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ManualCloseRequest req = new ManualCloseRequest(OFFICE_LAT, OFFICE_LON, "Normal end");

        // When
        tripController.manualClose(TRIP_ID, req);

        // Then: exactly one event saved — MANUAL_CLOSURE
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(1)).save(captor.capture());

        assertThat(captor.getValue().getEventType()).isEqualTo(GeofenceEventType.MANUAL_CLOSURE);

        // No admin alert — this is a normal close
        verify(notificationService, never()).sendAdminAlert(anyLong(), anyLong(), anyString());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Test 3 — Already completed trip: reject with 400, no event saved
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Manual close on already COMPLETED trip → 400 Bad Request, no audit event")
    void manualCloseAlreadyCompleted_returnsBadRequest() {
        // Given: trip is already COMPLETED
        Trip trip = buildActiveTrip();
        trip.setStatus("COMPLETED");
        trip.setEndTime(LocalDateTime.now().minusMinutes(10));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));

        ManualCloseRequest req = new ManualCloseRequest(OUTSIDE_LAT, OUTSIDE_LON, "Re-close attempt");

        // When
        ResponseEntity<?> response = tripController.manualClose(TRIP_ID, req);

        // Then: 400 Bad Request
        assertThat(response.getStatusCode().value()).isEqualTo(400);

        // No audit events must be saved
        verify(eventLogRepository, never()).save(any());

        // No notifications
        verify(notificationService, never()).sendAdminAlert(anyLong(), anyLong(), anyString());
    }
}
