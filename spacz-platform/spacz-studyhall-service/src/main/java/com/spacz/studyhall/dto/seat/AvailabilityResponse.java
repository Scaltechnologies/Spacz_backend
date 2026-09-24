package com.spacz.studyhall.dto.seat;

import java.time.LocalDate;
import java.util.List;

/**
 * @param totalSeats        all seats of the hall
 * @param availableSeats    bookable and free for the whole date range
 * @param occupiedSeats     bookable but booked/held on at least one of the dates
 * @param outOfServiceSeats RESERVED, MAINTENANCE or INACTIVE
 */
public record AvailabilityResponse(Long studyHallId, LocalDate startDate, LocalDate endDate, long totalSeats,
                                   long availableSeats, long occupiedSeats, long outOfServiceSeats,
                                   List<Long> availableSeatIds) {
}
