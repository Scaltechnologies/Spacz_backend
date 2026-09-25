package com.spacz.studyhall.dto.internal;

/**
 * @param outcome CREATED, ALREADY_IMPORTED, SKIPPED_SEAT_UNKNOWN or SKIPPED_OVERLAP
 */
public record ImportBookingResult(Long legacyBookingId, Long bookingId, String outcome) {
}
