package com.spacz.studyhall.entity;

import java.util.List;

public enum BookingStatus {
    /** Seat held until holdExpiresAt, waiting for confirmation (payment). */
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    EXPIRED;

    /** Statuses that occupy the seat (also used by the database exclusion constraint). */
    public static final List<BookingStatus> OCCUPYING = List.of(PENDING, CONFIRMED);
}
