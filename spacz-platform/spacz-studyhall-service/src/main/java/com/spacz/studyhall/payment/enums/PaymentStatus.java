package com.spacz.studyhall.payment.enums;

import java.util.List;

/**
 * ONLINE:      CREATED -> PENDING -> PROCESSING -> { SUCCESS | FAILED | CANCELLED | EXPIRED | REFUND_PENDING }
 * PAY_AT_HALL: PENDING -> [AWAITING_VENDOR_CONFIRMATION] -> { SUCCESS | CANCELLED | EXPIRED }
 *
 * SUCCESS is terminal and immutable. FAILED / CANCELLED / EXPIRED may go back to PENDING only via an
 * explicit retry (ONLINE), or forward to SUCCESS / REFUND_PENDING when the gateway later reports that
 * the money really was captured (a real charge is never discarded). REFUND_PENDING means money was
 * captured but the booking could not be confirmed (seat lost, duplicate payment, amount mismatch); only
 * the refund job moves it on, to REFUNDED.
 *
 * <p>The partial unique index uq_payments_one_open_per_booking (Flyway V12) lists the {@link #OPEN}
 * statuses: update it in the same change whenever an "open" status is added.
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    PROCESSING,
    AWAITING_VENDOR_CONFIRMATION,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED,
    /** Gateway captured money but the booking could not be confirmed. The refund job resolves it. */
    REFUND_PENDING,
    REFUNDED;

    public static final List<PaymentStatus> OPEN = List.of(CREATED, PENDING, PROCESSING, AWAITING_VENDOR_CONFIRMATION);

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == EXPIRED || this == REFUNDED;
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    /** Money is captured and accounted for (kept or being returned); nothing may change it again except the refund job. */
    public boolean isMoneySettled() {
        return this == SUCCESS || this == REFUND_PENDING || this == REFUNDED;
    }

    public boolean canTransitionTo(PaymentStatus next) {
        if (this == SUCCESS || this == REFUNDED) return false;
        if (this == next) return true;
        return switch (this) {
            case CREATED -> next == PENDING || next == CANCELLED || next == EXPIRED || next == FAILED;
            case PENDING -> next == PROCESSING || next == SUCCESS || next == FAILED || next == CANCELLED
                    || next == EXPIRED || next == AWAITING_VENDOR_CONFIRMATION || next == REFUND_PENDING;
            case PROCESSING -> next == SUCCESS || next == FAILED || next == CANCELLED || next == EXPIRED
                    || next == REFUND_PENDING;
            case AWAITING_VENDOR_CONFIRMATION -> next == SUCCESS || next == CANCELLED || next == EXPIRED || next == PENDING;
            // late gateway SUCCESS on a locally-closed payment: forward to SUCCESS, or REFUND_PENDING if the seat is lost
            case FAILED, CANCELLED, EXPIRED -> next == PENDING || next == SUCCESS || next == REFUND_PENDING;
            case REFUND_PENDING -> next == REFUNDED;
            case SUCCESS, REFUNDED -> false;
        };
    }
}
