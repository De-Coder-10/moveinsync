package com.moveinsync.vehicletracking.entity;

/**
 * Enum for all geofence-related audit event types.
 *
 * Used in EventLog.eventType to replace raw String constants,
 * providing compile-time type safety and preventing typos.
 *
 * Stored as a String in the DB via @Enumerated(EnumType.STRING).
 */
public enum GeofenceEventType {

    /** Vehicle entered a pickup geofence — employee notified */
    PICKUP_ARRIVED,

    /** Vehicle entered office geofence AND met dwell-time + speed criteria */
    OFFICE_REACHED,

    /** Trip auto-completed after OFFICE_REACHED validation passed */
    TRIP_COMPLETED,

    /** Vehicle exited office geofence before dwell time was met (EC2 — GPS drift reset) */
    GEOFENCE_EXIT,

    /** Driver manually closed trip while inside office geofence */
    MANUAL_CLOSURE,

    /** Driver manually closed trip OUTSIDE office geofence — triggers admin alert (EC4) */
    MANUAL_CLOSURE_OUTSIDE_GEOFENCE,

    /** Operations team alert fired for compliance review (EC4) */
    ADMIN_ALERT,

    /** Auto-closure was attempted but blocked because not all pickup points were ARRIVED (EC5) */
    TRIP_CLOSURE_BLOCKED_PENDING_PICKUPS,

    /** Auto-closure blocked because the trip has not yet reached the minimum duration threshold */
    TRIP_CLOSURE_BLOCKED_MIN_DURATION
}
