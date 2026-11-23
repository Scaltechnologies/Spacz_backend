package com.spacz.studyhall.payment.gateway;

import java.math.BigDecimal;

/**
 * Provider-agnostic seam for payment gateway operations. Controllers and PaymentServiceImpl depend
 * only on this interface: no Razorpay-specific type or call ever appears outside {@link RazorpayGateway}.
 */
public interface PaymentGateway {

    String getProviderName();

    /** Public key handed to the frontend so it can open the checkout widget, never the secret. */
    String getPublicKeyId();

    GatewayOrder createOrder(CreateOrderCommand command);

    /** Cryptographic proof that a given (orderId, paymentId) pair was genuinely signed by the gateway. */
    boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature);

    /** Reconciliation / defense-in-depth: asks the gateway directly for the current truth on an order. */
    GatewayStatusResult fetchOrderStatus(String gatewayOrderId);

    /** Verifies the webhook body's HMAC signature and parses it only if valid. */
    WebhookVerificationResult verifyAndParseWebhook(String rawBody, String signatureHeader);

    /**
     * Idempotent: returns an existing non-failed refund for this payment if one exists, else creates a
     * full refund of the captured amount. {@code amount} is informational (what we expect to return).
     */
    GatewayRefund ensureFullRefund(String gatewayPaymentId, BigDecimal amount, String receipt);
}
