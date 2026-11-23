package com.spacz.studyhall.payment.enums;

import org.springframework.http.HttpStatus;

/** Machine-readable error codes of the payments API (the {@code error} field of ApiError). */
public enum PaymentErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYMENT_CANCELLED(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYMENT_PENDING(HttpStatus.CONFLICT),
    PAYMENT_EXPIRED(HttpStatus.GONE),
    PAYMENT_ALREADY_SUCCESSFUL(HttpStatus.CONFLICT),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYMENT_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED),
    PAYMENT_RETRY_NOT_ALLOWED(HttpStatus.CONFLICT),
    PAYMENT_ALREADY_PROCESSING(HttpStatus.CONFLICT),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY),
    PAYMENT_GATEWAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_REFERENCE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),

    // ── Booking ──────────────────────────────────────────────────────────
    BOOKING_NOT_PAYABLE(HttpStatus.CONFLICT),
    BOOKING_HOLD_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY),

    // ── Pay at Hall ──────────────────────────────────────────────────────
    INVALID_QR(HttpStatus.NOT_FOUND),
    INVALID_PAYMENT_CODE(HttpStatus.UNPROCESSABLE_ENTITY),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    WRONG_VENDOR(HttpStatus.FORBIDDEN),
    PAYMENT_NOT_CONFIRMABLE(HttpStatus.CONFLICT),
    PAY_AT_HALL_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    /** Platform-wide (PAYMENT_MODE = ONLINE_ONLY) or this vendor's own switch is off. */
    PAY_AT_HALL_DISABLED(HttpStatus.FORBIDDEN),
    /** Platform-wide (PAYMENT_MODE = PAY_AT_HALL_ONLY): Razorpay checkout taken out of rotation. */
    ONLINE_PAYMENT_DISABLED(HttpStatus.FORBIDDEN);

    private final HttpStatus httpStatus;

    PaymentErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
