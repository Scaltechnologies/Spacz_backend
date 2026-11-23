package com.spacz.studyhall.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.studyhall.payment.exception.PaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RazorpayGatewayTest {

    private static final String BASE = "https://api.razorpay.test/v1";
    private static final GatewayCredentials CREDS = new GatewayCredentials("rzp_test_key", "key_secret", "hook_secret");

    private MockRestServiceServer server;
    private RazorpayGateway gateway;
    private PaymentGatewayConfigClient configClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        configClient = mock(PaymentGatewayConfigClient.class);
        when(configClient.getCredentials()).thenReturn(CREDS);
        gateway = new RazorpayGateway(restTemplate, new ObjectMapper(), configClient, BASE);
    }

    @Test
    void checkoutSignatureIsHmacSha256OfOrderPipePayment() throws Exception {
        String expected = hmac("order_1|pay_1", "key_secret");
        assertThat(gateway.verifyCheckoutSignature("order_1", "pay_1", expected)).isTrue();
        assertThat(gateway.verifyCheckoutSignature("order_1", "pay_2", expected)).isFalse();
        assertThat(gateway.verifyCheckoutSignature("order_1", "pay_1", "")).isFalse();
        assertThat(gateway.verifyCheckoutSignature("order_1", "pay_1", hmac("order_1|pay_1", "other"))).isFalse();
    }

    @Test
    void webhookSignatureCoversTheExactRawBody() throws Exception {
        String raw = "{\"id\":\"evt_1\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                + "{\"id\":\"pay_9\",\"order_id\":\"order_9\",\"status\":\"captured\",\"amount\":49950,\"currency\":\"INR\"}}}}";
        WebhookVerificationResult ok = gateway.verifyAndParseWebhook(raw, hmac(raw, "hook_secret"));
        assertThat(ok.isSignatureValid()).isTrue();
        assertThat(ok.getGatewayEventId()).isEqualTo("evt_1");
        assertThat(ok.getStatus().getState()).isEqualTo(GatewayPaymentState.SUCCESS);
        assertThat(ok.getStatus().getGatewayOrderId()).isEqualTo("order_9");
        assertThat(ok.getStatus().getAmount()).isEqualByComparingTo("499.50");

        // One changed byte (e.g. re-serialized JSON) invalidates it.
        assertThat(gateway.verifyAndParseWebhook(raw + " ", hmac(raw, "hook_secret")).isSignatureValid()).isFalse();
        assertThat(gateway.verifyAndParseWebhook(raw, null).isSignatureValid()).isFalse();
    }

    @Test
    void paiseConversion() {
        assertThat(RazorpayGateway.toPaise(new BigDecimal("499.50"))).isEqualTo(49950L);
        assertThat(RazorpayGateway.toPaise(new BigDecimal("100"))).isEqualTo(10000L);
        assertThat(RazorpayGateway.toPaise(new BigDecimal("10.005"))).isEqualTo(1001L); // HALF_UP
        assertThat(RazorpayGateway.fromPaise(49950)).isEqualByComparingTo("499.50");
    }

    @Test
    void statusMapping() throws Exception {
        ObjectMapper om = new ObjectMapper();
        assertThat(state(om, "captured")).isEqualTo(GatewayPaymentState.SUCCESS);
        assertThat(state(om, "failed")).isEqualTo(GatewayPaymentState.FAILED);
        assertThat(state(om, "authorized")).isEqualTo(GatewayPaymentState.PENDING);
        assertThat(state(om, "created")).isEqualTo(GatewayPaymentState.PENDING);
        assertThat(state(om, "refunded")).isEqualTo(GatewayPaymentState.UNKNOWN);
    }

    @Test
    void createOrderSendsPaiseWithBasicAuthAndAutoCapture() {
        server.expect(requestTo(BASE + "/orders")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + java.util.Base64.getEncoder()
                        .encodeToString("rzp_test_key:key_secret".getBytes(StandardCharsets.UTF_8))))
                .andExpect(jsonPath("$.amount").value(49950))
                .andExpect(jsonPath("$.payment_capture").value(1))
                .andExpect(jsonPath("$.receipt").value("payment-1-attempt-1"))
                .andRespond(withSuccess("{\"id\":\"order_A\",\"status\":\"created\"}", MediaType.APPLICATION_JSON));
        GatewayOrder order = gateway.createOrder(CreateOrderCommand.builder().amount(new BigDecimal("499.50"))
                .currency("INR").receipt("payment-1-attempt-1").build());
        assertThat(order.getGatewayOrderId()).isEqualTo("order_A");
        server.verify();
    }

    @Test
    void fetchOrderStatusPrefersACapturedPaymentElseTheMostRecentOne() {
        server.expect(requestTo(BASE + "/orders/order_1/payments")).andRespond(withSuccess("""
                {"items":[{"id":"pay_new","status":"failed","created_at":200,"amount":100},
                          {"id":"pay_ok","status":"captured","created_at":100,"amount":100}]}""", MediaType.APPLICATION_JSON));
        assertThat(gateway.fetchOrderStatus("order_1").getGatewayPaymentId()).isEqualTo("pay_ok");

        server.reset();
        server.expect(requestTo(BASE + "/orders/order_2/payments")).andRespond(withSuccess("""
                {"items":[{"id":"pay_old","status":"failed","created_at":100},
                          {"id":"pay_new","status":"authorized","created_at":300}]}""", MediaType.APPLICATION_JSON));
        GatewayStatusResult latest = gateway.fetchOrderStatus("order_2");
        assertThat(latest.getGatewayPaymentId()).isEqualTo("pay_new");
        assertThat(latest.getState()).isEqualTo(GatewayPaymentState.PENDING);

        server.reset();
        server.expect(requestTo(BASE + "/orders/order_3/payments")).andRespond(withSuccess("{\"items\":[]}",
                MediaType.APPLICATION_JSON));
        assertThat(gateway.fetchOrderStatus("order_3").getState()).isEqualTo(GatewayPaymentState.PENDING);

        server.reset();
        server.expect(requestTo(BASE + "/orders/order_4/payments")).andRespond(withBadRequest());
        assertThat(gateway.fetchOrderStatus("order_4").getState()).isEqualTo(GatewayPaymentState.UNKNOWN);
    }

    @Test
    void ensureFullRefundReusesAnExistingRefund() {
        server.expect(requestTo(BASE + "/payments/pay_1/refunds")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/payments/pay_1/refund")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"rfnd_1\",\"status\":\"pending\",\"amount\":10000}", MediaType.APPLICATION_JSON));
        assertThat(gateway.ensureFullRefund("pay_1", new BigDecimal("100.00"), "refund-1").getGatewayRefundId())
                .isEqualTo("rfnd_1");
        server.verify();

        // Called again (e.g. the first run's commit failed): finds the refund, does NOT create another.
        server.reset();
        server.expect(requestTo(BASE + "/payments/pay_1/refunds"))
                .andRespond(withSuccess("{\"items\":[{\"id\":\"rfnd_failed\",\"status\":\"failed\"},"
                        + "{\"id\":\"rfnd_1\",\"status\":\"processed\",\"amount\":10000}]}", MediaType.APPLICATION_JSON));
        GatewayRefund again = gateway.ensureFullRefund("pay_1", new BigDecimal("100.00"), "refund-1");
        assertThat(again.getGatewayRefundId()).isEqualTo("rfnd_1");
        assertThat(again.getAmount()).isEqualByComparingTo("100.00");
        server.verify();
    }

    @Test
    void unconfiguredGatewayFailsFastAndCredentialsNeverPrint() {
        when(configClient.getCredentials()).thenReturn(null);
        assertThatThrownBy(() -> gateway.getPublicKeyId())
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("PUT /api/admin/payment-gateway/razorpay");
        assertThat(CREDS.toString()).doesNotContain("key_secret").doesNotContain("hook_secret");
    }

    private static GatewayPaymentState state(ObjectMapper om, String rzpStatus) throws Exception {
        return RazorpayGateway.toStatusResult(om.readTree("{\"status\":\"" + rzpStatus + "\"}"), "o").getState();
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
