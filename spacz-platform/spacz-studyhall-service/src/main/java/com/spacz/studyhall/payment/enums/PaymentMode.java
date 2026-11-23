package com.spacz.studyhall.payment.enums;

/**
 * Platform-wide payment mode: admin-configurable (setting PAYMENT_MODE in admin-service, see
 * PaymentModeConfigClient) so an ADMIN can restrict checkout to Pay at Hall while Razorpay's live keys
 * aren't ready, without a redeploy. The per-vendor VendorProfile.payAtHallEnabled switch still applies
 * underneath it.
 */
public enum PaymentMode {
    ONLINE_ONLY,
    PAY_AT_HALL_ONLY,
    BOTH
}
