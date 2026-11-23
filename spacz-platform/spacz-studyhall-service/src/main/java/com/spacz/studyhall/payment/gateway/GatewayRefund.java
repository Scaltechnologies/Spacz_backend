package com.spacz.studyhall.payment.gateway;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class GatewayRefund {
    String gatewayRefundId;
    String status;
    BigDecimal amount;
}
