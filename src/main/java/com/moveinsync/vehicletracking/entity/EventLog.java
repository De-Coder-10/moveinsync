package com.moveinsync.vehicletracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Audit & Compliance Event Log
 *
 * Persists every geofence event with full audit fields for:
 *  - SLA validation          (timestamp per event shows exact arrival times)
 *  - Dispute resolution      (GPS coordinates provide evidence of vehicle location)
 *  - Client reporting        (queryable by tripId, vehicleId, or time range)
 *
 * Fields:
 *  - vehicleId       : which vehicle triggered the event
 *  - tripId          : which trip this event belongs to
 *  - eventType       : typed enum — no raw strings (GeofenceEventType)
 *  - latitude/lon    : GPS coordinates at event trigger time
 *  - timestamp       : SERVER time when event logic evaluated (not device time)
 *  - createdAt       : SERVER time when DB record was written
 *
 * DB Indexes:
 *  - idx_event_log_trip_id          : fast trip-based audit queries
 *  - idx_event_log_vehicle_id       : fast vehicle-based queries
 *  - idx_event_log_trip_event_type  : fast idempotency check (existsByTripIdAndEventType)
 */
@Entity
@Table(
    name = "event_logs",
    indexes = {
        @Index(name = "idx_event_log_trip_id",         columnList = "trip_id"),
        @Index(name = "idx_event_log_vehicle_id",      columnList = "vehicle_id"),
        @Index(name = "idx_event_log_trip_event_type", columnList = "trip_id, event_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which vehicle triggered this event */
    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    /** Which trip this event belongs to */
    @Column(name = "trip_id")
    private Long tripId;

    /**
     * Enum-typed event — prevents typos, enables IDE auto-complete.
     * Stored as a VARCHAR in the DB (EnumType.STRING).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private GeofenceEventType eventType;

    /** GPS latitude at the moment of event trigger */
    @Column(nullable = false)
    private Double latitude;

    /** GPS longitude at the moment of event trigger */
    @Column(nullable = false)
    private Double longitude;

    /**
     * Server-generated timestamp of when the event logic evaluated.
     * Always set to LocalDateTime.now() on the server — never from device/request payload.
     * Used for SLA validation and dispute resolution.
     */
    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * Server-generated timestamp of when this DB record was written.
     * Set automatically via @PrePersist — cannot be overwritten.
     * Useful for detecting audit pipeline delays in production.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA lifecycle hook: always stamp createdAt with server time before insert */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
