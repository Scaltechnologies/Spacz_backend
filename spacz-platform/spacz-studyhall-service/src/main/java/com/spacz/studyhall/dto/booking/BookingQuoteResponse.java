package com.spacz.studyhall.dto.booking;

import com.spacz.studyhall.entity.BookingPlan;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Price of a prospective booking, computed exactly as {@code POST /api/bookings} would.
 * {@code totalPrice = baseAmount - discountAmount + platformCharges + taxes}.
 *
 * @param units days (DAILY) or months (MONTHLY)
 */
public record BookingQuoteResponse(Long studyHallId, String studyHallName, Long seatId, String seatNumber,
                                   BookingPlan plan, LocalDate startDate, LocalDate endDate, long units,
                                   BigDecimal unitPrice, BigDecimal baseAmount, BigDecimal discountAmount,
                                   Long offerId, String offerTitle, BigDecimal platformCharges, BigDecimal taxes,
                                   BigDecimal totalPrice) {
}
