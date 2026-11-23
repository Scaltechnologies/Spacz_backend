package com.spacz.studyhall.payment.gateway;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WebhookVerificationResult {
    boolean signatureValid;
    /** Gateway-supplied dedupe id when available; null if the caller must derive one from order+payment+eventType. */
    String gatewayEventId;
    String eventType;
    GatewayStatusResult status;
    String rawJson;
}
