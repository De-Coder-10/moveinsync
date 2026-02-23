package com.moveinsync.vehicletracking.service;

import com.moveinsync.vehicletracking.dto.LocationUpdateRequest;
import com.moveinsync.vehicletracking.entity.*;
import com.moveinsync.vehicletracking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LocationService — Audit & Compliance coverage.
 *
 * Test cases:
 *  1. pickupArrived_persistsAuditEventWithServerTimestamp
 *     Vehicle enters pickup geofence → PICKUP_ARRIVED audit event must be saved.
 *
 *  2. officeReachedAndTripCompleted_persistsTwoAuditEvents
 *     Vehicle enters office geofence, meets dwell time + speed criteria →
 *     OFFICE_REACHED and TRIP_COMPLETED events must be saved.
 *
 *  3. duplicatePickupPacket_doesNotCreateDuplicateAuditEvent
 *     Second ping inside pickup geofence when status is already ARRIVED →
 *     no new EventLog must be saved (idempotency).
 *
 *  4. alreadyCompletedTrip_officeGeofence_noAuditEvent
 *     Ping inside office geofence but trip is already COMPLETED →
 *     no duplicate OFFICE_REACHED / TRIP_COMPLETED events.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    // ── Mocks ────────────────────────────────────────────────────────────────

    @Mock private LocationLogRepository   locationLogRepository;
    @Mock private TripRepository          tripRepository;
    @Mock private PickupPointRepository   pickupPointRepository;
    @Mock private OfficeGeofenceRepository officeGeofenceRepository;
    @Mock private EventLogRepository      eventLogRepository;
    @Mock private NotificationService     notificationService;
    @Mock private SimpMessagingTemplate   messagingTemplate;

    @InjectMocks
    private LocationService locationService;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private static final Long VEHICLE_ID = 1L;
    private static final Long TRIP_ID    = 1L;

    /** Pickup point geofence: Lat=12.952, Lon=77.575, Radius=50m */
    private static final double PICKUP_LAT    = 12.952;
    private static final double PICKUP_LON    = 77.575;
    private static final double PICKUP_RADIUS = 50.0;

    /** Office geofence: Lat=12.9716, Lon=77.5946, Radius=100m */
    private static final double OFFICE_LAT    = 12.9716;
    private static final double OFFICE_LON    = 77.5946;
    private static final double OFFICE_RADIUS = 100.0;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Spring cannot set in unit tests
        ReflectionTestUtils.setField(locationService, "dwellTimeSeconds",   30);
        ReflectionTestUtils.setField(locationService, "speedThresholdKmh", 5.0);
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    private Trip buildTrip(String status) {
        return Trip.builder()
                .id(TRIP_ID)
                .vehicle(Vehicle.builder().id(VEHICLE_ID).registrationNumber("KA01AB1234").status("ACTIVE").build())
                .status(status)
                .startTime(LocalDateTime.now().minusHours(1))
                .totalDistanceKm(0.0)
                .build();
    }

    private Trip buildTripWithOfficeEntry(int secondsAgo) {
        Trip trip = buildTrip("IN_PROGRESS");
        trip.setOfficeEntryTime(LocalDateTime.now().minusSeconds(secondsAgo));
        return trip;
    }

    private LocationUpdateRequest buildRequest(double lat, double lon, double speed) {
        return LocationUpdateRequest.builder()
                .vehicleId(VEHICLE_ID)
                .tripId(TRIP_ID)
                .latitude(lat)
                .longitude(lon)
                .speed(speed)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private PickupPoint buildPickup(String status) {
        Trip trip = buildTrip("IN_PROGRESS");
        return PickupPoint.builder()
                .id(1L)
                .trip(trip)
                .latitude(PICKUP_LAT)
                .longitude(PICKUP_LON)
                .radiusMeters(PICKUP_RADIUS)
                .status(status)
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
    // Test 1 — PICKUP_ARRIVED audit event is saved with server timestamp
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Vehicle enters pickup geofence → PICKUP_ARRIVED audit event persisted")
    void pickupArrived_persistsAuditEventWithServerTimestamp() {
        // Given: vehicle exactly on pickup point (distance = 0m, well within 50m radius)
        LocationUpdateRequest request = buildRequest(PICKUP_LAT, PICKUP_LON, 15.0);

        Trip trip = buildTrip("IN_PROGRESS");
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(locationLogRepository.findTopByTripIdOrderByTimestampDesc(TRIP_ID))
                .thenReturn(Optional.empty());
        when(locationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PickupPoint pendingPickup = buildPickup("PENDING");
        when(pickupPointRepository.findAllByTripId(TRIP_ID)).thenReturn(List.of(pendingPickup));

        // No office geofence — prevents office-check branch from running
        when(officeGeofenceRepository.findAll()).thenReturn(List.of());

        // When
        locationService.processLocationUpdate(request);

        // Then: EventLog was saved exactly once with PICKUP_ARRIVED
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(1)).save(captor.capture());

        EventLog saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(GeofenceEventType.PICKUP_ARRIVED);
        assertThat(saved.getVehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(saved.getTripId()).isEqualTo(TRIP_ID);
        assertThat(saved.getLatitude()).isEqualTo(PICKUP_LAT);
        assertThat(saved.getLongitude()).isEqualTo(PICKUP_LON);

        // Server timestamp must be set (not null, not device timestamp)
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());

        // Notification must also fire
        verify(notificationService).sendPickupArrivalNotification(VEHICLE_ID, TRIP_ID, PICKUP_LAT, PICKUP_LON);

        // Pickup status must be updated to ARRIVED
        assertThat(pendingPickup.getStatus()).isEqualTo("ARRIVED");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Test 2 — OFFICE_REACHED + TRIP_COMPLETED both saved
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Vehicle in office geofence with dwell time met → OFFICE_REACHED + TRIP_COMPLETED persisted")
    void officeReachedAndTripCompleted_persistsTwoAuditEvents() {
        // Given: vehicle exactly on office location, slow speed, dwell time = 35s (> 30s threshold)
        LocationUpdateRequest request = buildRequest(OFFICE_LAT, OFFICE_LON, 2.0);

        Trip trip = buildTripWithOfficeEntry(35); // 35s dwell — meets 30s threshold
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(locationLogRepository.findTopByTripIdOrderByTimestampDesc(TRIP_ID))
                .thenReturn(Optional.empty());
        when(locationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // No pickup points — EC5 check passes trivially
        when(pickupPointRepository.findAllByTripId(TRIP_ID)).thenReturn(List.of());

        // Pickup geofence check: no pickup points
        when(officeGeofenceRepository.findAll()).thenReturn(List.of(buildOfficeGeofence()));

        // EC6 idempotency check: OFFICE_REACHED not yet logged
        when(eventLogRepository.existsByTripIdAndEventType(TRIP_ID, GeofenceEventType.OFFICE_REACHED))
                .thenReturn(false);

        // When
        locationService.processLocationUpdate(request);

        // Then: two audit events — OFFICE_REACHED and TRIP_COMPLETED
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(2)).save(captor.capture());

        List<EventLog> savedEvents = captor.getAllValues();
        List<GeofenceEventType> types = savedEvents.stream()
                .map(EventLog::getEventType)
                .toList();

        assertThat(types).containsExactlyInAnyOrder(
                GeofenceEventType.OFFICE_REACHED,
                GeofenceEventType.TRIP_COMPLETED);

        // All events must have server timestamps
        savedEvents.forEach(e -> {
            assertThat(e.getTimestamp()).isNotNull();
            assertThat(e.getVehicleId()).isEqualTo(VEHICLE_ID);
            assertThat(e.getTripId()).isEqualTo(TRIP_ID);
        });

        // Trip must be marked COMPLETED
        assertThat(trip.getStatus()).isEqualTo("COMPLETED");

        // Completion notification must fire
        verify(notificationService).sendTripCompletionNotification(VEHICLE_ID, TRIP_ID);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Test 3 — Idempotency: duplicate ping does NOT create duplicate PICKUP_ARRIVED
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Pickup already ARRIVED — second ping inside geofence does NOT save duplicate event")
    void duplicatePickupPacket_doesNotCreateDuplicateAuditEvent() {
        // Given: pickup status is already ARRIVED (first ping already processed)
        LocationUpdateRequest request = buildRequest(PICKUP_LAT, PICKUP_LON, 10.0);

        Trip trip = buildTrip("IN_PROGRESS");
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(locationLogRepository.findTopByTripIdOrderByTimestampDesc(TRIP_ID))
                .thenReturn(Optional.empty());
        when(locationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Pickup already ARRIVED — idempotency guard should skip
        PickupPoint arrivedPickup = buildPickup("ARRIVED");
        when(pickupPointRepository.findAllByTripId(TRIP_ID)).thenReturn(List.of(arrivedPickup));

        // No office geofence
        when(officeGeofenceRepository.findAll()).thenReturn(List.of());

        // When
        locationService.processLocationUpdate(request);

        // Then: NO EventLog must be saved for PICKUP_ARRIVED
        verify(eventLogRepository, never()).save(argThat(e ->
                e.getEventType() == GeofenceEventType.PICKUP_ARRIVED));

        // And no notification for an already-arrived pickup
        verify(notificationService, never()).sendPickupArrivalNotification(
                anyLong(), anyLong(), anyDouble(), anyDouble());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Test 4 — Already-completed trip: no duplicate OFFICE_REACHED / TRIP_COMPLETED
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Trip already COMPLETED — ping inside office geofence does NOT create duplicate events")
    void alreadyCompletedTrip_officeGeofence_noAuditEvent() {
        // Given: vehicle inside office geofence, but trip is already COMPLETED
        LocationUpdateRequest request = buildRequest(OFFICE_LAT, OFFICE_LON, 1.0);

        Trip trip = buildTrip("COMPLETED"); // trip already closed
        trip.setEndTime(LocalDateTime.now().minusMinutes(5));
        when(tripRepository.findByIdForUpdate(TRIP_ID)).thenReturn(Optional.of(trip));
        when(locationLogRepository.findTopByTripIdOrderByTimestampDesc(TRIP_ID))
                .thenReturn(Optional.empty());
        when(locationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(pickupPointRepository.findAllByTripId(TRIP_ID)).thenReturn(List.of());
        when(officeGeofenceRepository.findAll()).thenReturn(List.of(buildOfficeGeofence()));

        // When
        locationService.processLocationUpdate(request);

        // Then: no OFFICE_REACHED or TRIP_COMPLETED events must be saved
        verify(eventLogRepository, never()).save(argThat(e ->
                e.getEventType() == GeofenceEventType.OFFICE_REACHED
                || e.getEventType() == GeofenceEventType.TRIP_COMPLETED));

        // No completion notification
        verify(notificationService, never()).sendTripCompletionNotification(anyLong(), anyLong());
    }
}
