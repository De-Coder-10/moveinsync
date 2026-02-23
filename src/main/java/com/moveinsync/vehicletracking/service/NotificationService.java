package com.moveinsync.vehicletracking.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification Service — Functionality #5
 *
 * Simulates sending push notifications to employee app and SMS fallback.
 * In production, replace the log statements with:
 *   - Firebase FCM  → firebaseMessaging.send(message) for push notifications
 *   - Twilio / AWS SNS → for SMS fallback when app is inactive
 */
@Service
@Slf4j
public class NotificationService {

    /**
     * Triggered when vehicle enters pickup geofence (PICKUP_ARRIVED event)
     * Sends push notification + SMS fallback to the employee
     */
    public void sendPickupArrivalNotification(Long vehicleId, Long tripId, double lat, double lon) {
        log.info("================================================================");
        log.info("[PUSH NOTIFICATION] 🚕 Your cab has arrived at the pickup point!");
        log.info("[PUSH NOTIFICATION] Vehicle ID: {}, Trip ID: {}", vehicleId, tripId);
        log.info("[PUSH NOTIFICATION] Location: ({}, {})", lat, lon);
        log.info("[SMS FALLBACK] Sending SMS: 'Your cab KA01AB1234 has arrived. Please come down.'");
        log.info("================================================================");
        // TODO (Production): Firebase FCM integration
        //   Message message = Message.builder()
        //       .setNotification(Notification.builder().setTitle("Cab Arrived!").setBody("Your cab is here").build())
        //       .setToken(employeeDeviceToken).build();
        //   firebaseMessaging.send(message);
        //
        // TODO (Production): SMS fallback via Twilio
        //   Message.creator(new PhoneNumber(employeePhone), twilioFrom, "Cab arrived!").create();
    }

    /**
     * Triggered when trip is auto-closed (TRIP_COMPLETED event)
     * Notifies admin/employee that the trip has ended
     */
    public void sendTripCompletionNotification(Long vehicleId, Long tripId) {
        log.info("================================================================");
        log.info("[PUSH NOTIFICATION] ✅ Trip #{} has been completed!", tripId);
        log.info("[PUSH NOTIFICATION] Vehicle ID: {} has reached the office.", vehicleId);
        log.info("[SMS FALLBACK] Sending SMS: 'Your trip has been completed. Have a great day!'");
        log.info("================================================================");
    }
}
