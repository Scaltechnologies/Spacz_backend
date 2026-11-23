package com.spacz.studyhall.payment.dto;

import com.spacz.studyhall.payment.enums.PaymentStatus;
import com.spacz.studyhall.payment.enums.VerificationMethod;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** What a vendor sees for a Pay-at-Hall order (never the QR token or code). */
@Builder
public record VendorPayAtHallResponse(Long paymentId, Long userId, Long bookingId, String bookingReference,
                                      Long studyHallId, Long seatId, String seatNumber, LocalDate startDate,
                                      LocalDate endDate, String description, BigDecimal amount, String currency,
                                      PaymentStatus status, Instant createdAt, Instant expiresAt, Instant confirmedAt,
                                      Long confirmedByVendorId, VerificationMethod confirmationMethod) {
}
