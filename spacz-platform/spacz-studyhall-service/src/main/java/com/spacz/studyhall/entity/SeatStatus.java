package com.spacz.studyhall.entity;

/**
 * The vendor's setting for a seat. Whether a seat is free on given dates is derived from bookings.
 */
public enum SeatStatus {
    AVAILABLE,
    /** Held by the vendor (walk-in / offline student); not bookable online. Legacy {@code is_reserved}. */
    RESERVED,
    MAINTENANCE,
    INACTIVE
}
