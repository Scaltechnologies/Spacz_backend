package com.spacz.studyhall.client.dto;

/**
 * @param type an ActivityType name of user-service (BOOKING_CREATED, BOOKING_CONFIRMED ...)
 */
public record RecordActivityRequest(String type, String description, String referenceType, Long referenceId) {
}
