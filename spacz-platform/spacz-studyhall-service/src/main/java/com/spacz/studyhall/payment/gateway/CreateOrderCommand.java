package com.spacz.studyhall.payment.gateway;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
@Builder
public class CreateOrderCommand {
    BigDecimal amount;
    String currency;
    /** Razorpay limits receipts to 40 characters. */
    String receipt;
    Map<String, String> notes;
}
