package com.spacz.studyhall.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.studyhall.payment.enums.PaymentErrorCode;
import com.spacz.studyhall.payment.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Talks to Razorpay's REST API directly (Basic Auth with key_id/key_secret) rather than pulling in
 * the Razorpay SDK, with the gateway-specific bits (HMAC signing, amount-in-paise, response shape)
 * isolated entirely inside this class. No other class in the codebase should ever import a
 * Razorpay-specific type or URL.
 *
 * Credentials are never held as static config here: they come from {@link PaymentGatewayConfigClient}
 * on every call, so an admin rotating keys via the dashboard takes effect within one cache TTL,
 * with no restart of this service.
 */
@Slf4j
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentGatewayConfigClient configClient;
    private final String baseUrl;

    public RazorpayGateway(@Qualifier("razorpayRestTemplate") RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           PaymentGatewayConfigClient configClient,
                           @Value("${razorpay.base-url:https://api.razorpay.com/v1}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.configClient = configClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getProviderName() {
        return "RAZORPAY";
    }

    @Override
    public String getPublicKeyId() {
        return requireConfigured().keyId();
    }

    @Override
    public GatewayOrder createOrder(CreateOrderCommand command) {
        GatewayCredentials creds = requireConfigured();

        long amountPaise = toPaise(command.getAmount());
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", command.getCurrency());
        body.put("receipt", command.getReceipt());
        body.put("payment_capture", 1);
        if (command.getNotes() != null && !command.getNotes().isEmpty()) {
            body.put("notes", command.getNotes());
        }

        JsonNode response = post("/orders", body, creds);
        String orderId = textOrNull(response, "id");
        if (orderId == null) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, "Gateway did not return an order id");
        }
        return GatewayOrder.builder()
                .gatewayOrderId(orderId)
                .amountPaise(amountPaise)
                .currency(command.getCurrency())
                .status(textOrNull(response, "status"))
                .build();
    }

    @Override
    public boolean verifyCheckoutSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
        GatewayCredentials creds = requireConfigured();
        if (signature == null || signature.isBlank()) return false;
        String expected = hmacHex(gatewayOrderId + "|" + gatewayPaymentId, creds.keySecret());
        return constantTimeEquals(expected, signature);
    }

    @Override
    public GatewayStatusResult fetchOrderStatus(String gatewayOrderId) {
        GatewayCredentials creds = requireConfigured();
        JsonNode response;
        try {
            response = get("/orders/" + gatewayOrderId + "/payments", creds);
        } catch (RestClientException | PaymentException e) {
            log.warn("Razorpay order status fetch failed for order={}: {}", gatewayOrderId, e.getMessage());
            return GatewayStatusResult.unknown(gatewayOrderId);
        }
        if (response == null) {
            return GatewayStatusResult.unknown(gatewayOrderId);
        }

        JsonNode items = response.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return GatewayStatusResult.builder().state(GatewayPaymentState.PENDING).gatewayOrderId(gatewayOrderId).build();
        }

        // A captured payment anywhere in the list means the order is genuinely paid regardless of
        // earlier failures. Otherwise the most recent attempt (max created_at) is authoritative:
        // Razorpay does not guarantee the collection's order, so "first item" is not "latest".
        JsonNode latest = null;
        for (JsonNode item : items) {
            if ("captured".equals(textOrNull(item, "status"))) {
                return toStatusResult(item, gatewayOrderId);
            }
            if (latest == null || item.path("created_at").asLong(0) > latest.path("created_at").asLong(0)) {
                latest = item;
            }
        }
        return toStatusResult(latest, gatewayOrderId);
    }

    @Override
    public WebhookVerificationResult verifyAndParseWebhook(String rawBody, String signatureHeader) {
        GatewayCredentials creds = requireWebhookConfigured();
        boolean valid = rawBody != null && signatureHeader != null && !signatureHeader.isBlank()
                && constantTimeEquals(hmacHex(rawBody, creds.webhookSecret()), signatureHeader);

        if (!valid) {
            return WebhookVerificationResult.builder().signatureValid(false).build();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Webhook signature valid but body is not parseable JSON: {}", e.getMessage());
            return WebhookVerificationResult.builder().signatureValid(false).build();
        }

        String eventType = textOrNull(root, "event");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String gatewayOrderId = textOrNull(paymentEntity, "order_id");
        if (gatewayOrderId == null) {
            gatewayOrderId = textOrNull(root.path("payload").path("order").path("entity"), "id");
        }
        GatewayStatusResult status = paymentEntity.isMissingNode()
                ? GatewayStatusResult.unknown(gatewayOrderId)
                : toStatusResult(paymentEntity, gatewayOrderId);

        return WebhookVerificationResult.builder()
                .signatureValid(true)
                .gatewayEventId(textOrNull(root, "id"))
                .eventType(eventType != null ? eventType : "unknown")
                .status(status)
                .rawJson(rawBody)
                .build();
    }

    @Override
    public GatewayRefund ensureFullRefund(String gatewayPaymentId, BigDecimal amount, String receipt) {
        GatewayCredentials creds = requireConfigured();
        JsonNode existing = get("/payments/" + gatewayPaymentId + "/refunds", creds);
        if (existing != null) {
            for (JsonNode item : existing.path("items")) {
                String st = textOrNull(item, "status");
                if (!"failed".equals(st)) {
                    return GatewayRefund.builder().gatewayRefundId(textOrNull(item, "id")).status(st)
                            .amount(item.hasNonNull("amount") ? fromPaise(item.get("amount").asLong()) : null).build();
                }
            }
        }
        // No amount in the body: Razorpay refunds the full captured amount, which is exactly what we
        // owe (even when the captured amount differed from the booking total).
        Map<String, Object> body = new HashMap<>();
        body.put("receipt", receipt);
        JsonNode r = post("/payments/" + gatewayPaymentId + "/refund", body, creds);
        return GatewayRefund.builder().gatewayRefundId(textOrNull(r, "id")).status(textOrNull(r, "status"))
                .amount(r != null && r.hasNonNull("amount") ? fromPaise(r.get("amount").asLong()) : amount).build();
    }

    // ── internals ────────────────────────────────────────────────────────────

    static GatewayStatusResult toStatusResult(JsonNode paymentNode, String gatewayOrderId) {
        String rzpStatus = textOrNull(paymentNode, "status");
        GatewayPaymentState state = switch (rzpStatus == null ? "" : rzpStatus) {
            case "captured" -> GatewayPaymentState.SUCCESS;
            case "failed" -> GatewayPaymentState.FAILED;
            case "authorized", "created" -> GatewayPaymentState.PENDING;
            default -> GatewayPaymentState.UNKNOWN;
        };
        Long amountPaise = paymentNode.hasNonNull("amount") ? paymentNode.get("amount").asLong() : null;
        return GatewayStatusResult.builder()
                .state(state)
                .gatewayOrderId(gatewayOrderId != null ? gatewayOrderId : textOrNull(paymentNode, "order_id"))
                .gatewayPaymentId(textOrNull(paymentNode, "id"))
                .amount(amountPaise != null ? fromPaise(amountPaise) : null)
                .currency(textOrNull(paymentNode, "currency"))
                .errorCode(textOrNull(paymentNode, "error_code"))
                .errorDescription(textOrNull(paymentNode, "error_description"))
                .build();
    }

    private JsonNode post(String path, Map<String, Object> body, GatewayCredentials creds) {
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, authHeaders(creds));
        try {
            return restTemplate.postForObject(baseUrl + path, entity, JsonNode.class);
        } catch (HttpStatusCodeException e) {
            throw gatewayError(e);
        } catch (RestClientException e) {
            log.warn("Razorpay unreachable on POST {}: {}", path, e.getMessage());
            throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, "Payment gateway is currently unavailable");
        }
    }

    private JsonNode get(String path, GatewayCredentials creds) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(creds));
        try {
            return restTemplate.exchange(baseUrl + path, HttpMethod.GET, entity, JsonNode.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw gatewayError(e);
        }
    }

    private PaymentException gatewayError(HttpStatusCodeException e) {
        // The response body is Razorpay's error JSON (code/description); it never echoes our secret.
        log.warn("Razorpay API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        if (e.getStatusCode().is4xxClientError()) {
            return new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, "Payment gateway rejected the request");
        }
        return new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, "Payment gateway is currently unavailable");
    }

    private static HttpHeaders authHeaders(GatewayCredentials creds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(creds.keyId(), creds.keySecret());
        return headers;
    }

    private GatewayCredentials requireConfigured() {
        GatewayCredentials creds = configClient.getCredentials();
        if (creds == null || creds.keyId() == null || creds.keyId().isBlank()
                || creds.keySecret() == null || creds.keySecret().isBlank()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
                    "Payment gateway credentials are not configured. An ADMIN must set them at "
                            + "PUT /api/admin/payment-gateway/razorpay before online payments can be created.");
        }
        return creds;
    }

    private GatewayCredentials requireWebhookConfigured() {
        GatewayCredentials creds = configClient.getCredentials();
        if (creds == null || creds.webhookSecret() == null || creds.webhookSecret().isBlank()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
                    "Payment gateway webhook secret is not configured on this environment");
        }
        return creds;
    }

    static long toPaise(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    static String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
