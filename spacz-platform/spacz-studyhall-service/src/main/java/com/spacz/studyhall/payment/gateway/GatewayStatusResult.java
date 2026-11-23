package com.spacz.studyhall.payment.gateway;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Authoritative status of one payment try, as reported directly by the gateway (fetch/verify/webhook alike). */
@Value
@Builder
public class GatewayStatusResult {
    GatewayPaymentState state;
    String gatewayOrderId;
    String gatewayPaymentId;
    BigDecimal amount;
    String currency;
    String errorCode;
    String errorDescription;

    public static GatewayStatusResult unknown(String gatewayOrderId) {
        return GatewayStatusResult.builder().state(GatewayPaymentState.UNKNOWN).gatewayOrderId(gatewayOrderId).build();
    }
}
