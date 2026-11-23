package com.spacz.studyhall.payment.dto;

import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Internal (admin-service) views of payments. */
public final class PaymentAdminViews {

    private PaymentAdminViews() {
    }

    public record AuditEventView(Long id, Long paymentId, String actorType, Long actorId, String action,
                                 String previousStatus, String newStatus, String metadata, Instant createdAt) {
    }

    public record AttemptView(int attemptNumber, String gatewayProvider, String gatewayOrderId, String gatewayPaymentId,
                              PaymentStatus status, String failureCode, String failureReason, Boolean signatureVerified,
                              Instant createdAt) {
    }

    public record PaymentView(Long paymentId, Long bookingId, String bookingReference, Long userId, Long vendorId,
                              PaymentMethod paymentMethod, PaymentStatus status, BigDecimal amount, String currency,
                              String failureReason, Instant expiresAt, String gatewayRefundId, Instant refundedAt,
                              Instant createdAt, Instant updatedAt, java.util.List<AttemptView> attempts) {
    }
}
