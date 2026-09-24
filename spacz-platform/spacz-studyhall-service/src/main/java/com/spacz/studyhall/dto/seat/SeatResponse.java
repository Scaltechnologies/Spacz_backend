package com.spacz.studyhall.dto.seat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.SeatType;

import java.math.BigDecimal;

/**
 * @param pricePerDay   effective price (seat → block → hall)
 * @param pricePerMonth effective monthly price; null = monthly bookings not offered
 * @param available     only on date-based seat maps: bookable for the requested dates
 */
public record SeatResponse(Long id, Long blockId, String seatNumber, int row, int column, SeatType seatType,
                           SeatStatus status, BigDecimal pricePerDay, BigDecimal pricePerMonth,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Boolean available) {
}
