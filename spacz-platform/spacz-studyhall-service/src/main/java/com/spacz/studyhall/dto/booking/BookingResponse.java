package com.spacz.studyhall.dto.booking;

import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @param units     days (DAILY) or months (MONTHLY)
 * @param unitPrice price per day or per month at booking time
 */
public record BookingResponse(Long id, String bookingReference, Long userId, Long studyHallId, String studyHallName,
                              String studyHallCity, Long seatId, String seatNumber, Long programId, String programName,
                              BookingPlan plan, LocalDate startDate, LocalDate endDate, long units,
                              BigDecimal unitPrice, BigDecimal totalPrice, BookingStatus status, Instant holdExpiresAt,
                              Instant confirmedAt, Instant cancelledAt, String cancellationReason, Instant createdAt,
                              Instant updatedAt) {
}
