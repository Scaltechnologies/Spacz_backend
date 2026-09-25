package com.spacz.admin.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingDto(Long id, String bookingReference, Long userId, Long studyHallId, String studyHallName,
                         String studyHallCity, Long seatId, String seatNumber, Long programId, String programName,
                         String plan, LocalDate startDate, LocalDate endDate, long units, BigDecimal unitPrice,
                         BigDecimal totalPrice, String status, Instant holdExpiresAt, Instant confirmedAt,
                         Instant cancelledAt, String cancellationReason, Instant createdAt) {
}
