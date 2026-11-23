package com.spacz.studyhall.payment.gateway;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GatewayOrder {
    String gatewayOrderId;
    long amountPaise;
    String currency;
    String status;
}
