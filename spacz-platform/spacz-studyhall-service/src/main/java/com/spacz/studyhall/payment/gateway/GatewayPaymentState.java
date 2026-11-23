package com.spacz.studyhall.payment.gateway;

/** Normalized outcome the gateway reports for an order/payment, independent of provider-specific status strings. */
public enum GatewayPaymentState {
    SUCCESS,
    FAILED,
    PENDING,
    UNKNOWN
}
