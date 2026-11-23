package com.spacz.studyhall.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A student's view of a payment.
 *
 * @param gatewayOrderId    present while the payment is open: what the client needs to open checkout
 * @param gatewayKeyId      the gateway's PUBLIC key id (never the secret), present while open
 * @param qrPayload         Pay at Hall: present ONLY in the response to the create call that generated it.
 *                          Never persisted in plaintext and never returned by any later read.
 * @param verificationCode  Pay at Hall: same as qrPayload
 */
@Builder(toBuilder = true)
public record PaymentResponse(Long paymentId, PaymentStatus status, PaymentMethod paymentMethod, Long bookingId,
                              String bookingReference, Long studyHallId, Long seatId, Long vendorId,
                              String description, BigDecimal amount, String currency, String failureReason,
                              Instant expiresAt, Instant confirmedAt, String gatewayProvider, String gatewayOrderId,
                              String gatewayKeyId, int attemptNumber,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String qrPayload,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String verificationCode) {

    /** Never let the one-time Pay-at-Hall secrets reach a log line through toString. */
    @Override
    public String toString() {
        return "PaymentResponse[paymentId=" + paymentId + ", status=" + status + ", method=" + paymentMethod + "]";
    }
}
