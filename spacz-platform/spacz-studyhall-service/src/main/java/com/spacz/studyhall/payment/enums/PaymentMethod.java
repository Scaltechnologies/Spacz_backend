package com.spacz.studyhall.payment.enums;

/**
 * ONLINE: Razorpay checkout, money flows through SPACZ's gateway account.
 * PAY_AT_HALL: the student pays the study hall at the counter; the vendor confirms with a QR scan or
 * the 6-digit code and then an explicit "payment received" action. No money moves through SPACZ.
 */
public enum PaymentMethod {
    ONLINE,
    PAY_AT_HALL
}
