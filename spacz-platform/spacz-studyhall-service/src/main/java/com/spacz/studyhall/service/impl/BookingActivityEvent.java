package com.spacz.studyhall.service.impl;

/**
 * Published inside the booking transaction; delivered to user-service only after commit.
 *
 * @param activityType an ActivityType name in user-service
 */
public record BookingActivityEvent(Long userId, String activityType, String description, Long bookingId) {
}
