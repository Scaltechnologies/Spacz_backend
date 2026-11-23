package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.payment.enums.PaymentErrorCode;
import com.spacz.studyhall.payment.enums.PaymentMode;
import com.spacz.studyhall.payment.exception.PaymentException;
import com.spacz.studyhall.payment.gateway.CreateOrderCommand;
import com.spacz.studyhall.payment.gateway.GatewayOrder;
import com.spacz.studyhall.payment.gateway.GatewayPaymentState;
import com.spacz.studyhall.payment.gateway.GatewayRefund;
import com.spacz.studyhall.payment.gateway.GatewayStatusResult;
import com.spacz.studyhall.payment.gateway.PaymentGateway;
import com.spacz.studyhall.payment.gateway.PaymentModeConfigClient;
import com.spacz.studyhall.payment.gateway.WebhookVerificationResult;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Booking payments end to end through the HTTP API (H2, Razorpay mocked at the PaymentGateway seam):
 * online checkout, webhook, cancel, retry, reconciliation, late success / refunds, and Pay at Hall.
 * The concurrency guarantees that need PostgreSQL row locks and partial unique indexes are in
 * PaymentRacePostgresTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest extends ApiTestSupport {

    private static final AtomicLong IDS = new AtomicLong(88_000);

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private PaymentGateway gateway;
    @MockitoBean
    private PaymentModeConfigClient modeClient;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbc;

    private long vendorId;
    private long hallId;
    private long seatId;
    private long secondSeatId;

    @BeforeEach
    void setUp() throws Exception {
        when(modeClient.getCurrentMode()).thenReturn(PaymentMode.BOTH);
        when(gateway.getProviderName()).thenReturn("RAZORPAY");
        when(gateway.getPublicKeyId()).thenReturn("rzp_test_public");
        when(gateway.createOrder(any(CreateOrderCommand.class))).thenAnswer(inv -> GatewayOrder.builder()
                .gatewayOrderId("order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14))
                .amountPaise(0).currency("INR").status("created").build());
        when(gateway.verifyCheckoutSignature(anyString(), anyString(), eq("good-signature"))).thenReturn(true);

        vendorId = IDS.incrementAndGet();
        approvedVendor(vendorId);
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Pay Hall %d","addressLine":"Road 1","city":"Hyderabad","state":"Telangana",
                 "pricePerDay":120.00,"openingTime":"06:00","closingTime":"22:00"}""".formatted(vendorId))
                .andExpect(status().isCreated()), "id");
        JsonNode block = json(send(post("/api/studyhalls/{id}/blocks", hallId), vendor,
                "{\"name\":\"Main\",\"totalRows\":1,\"totalColumns\":2,\"dailyPrice\":120.00}").andExpect(status().isCreated()));
        seatId = block.path("seats").get(0).path("id").asLong();
        secondSeatId = block.path("seats").get(1).path("id").asLong();
        send(post("/api/studyhalls/{id}/submit", hallId), vendor, null).andExpect(status().isOk());
        internal(patch("/internal/studyhalls/{id}/status", hallId), "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
    }

    // ── Online checkout ─────────────────────────────────────────────────────

    @Test
    void verifiedCapturedPaymentConfirmsTheBookingExactlyOnce() throws Exception {
        long student = IDS.incrementAndGet();
        JsonNode booking = book(student, seatId, 3);
        long bookingId = booking.path("id").asLong();

        JsonNode payment = json(createPayment(student, bookingId, null, "key-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMethod").value("ONLINE"))
                .andExpect(jsonPath("$.gatewayKeyId").value("rzp_test_public"))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.qrPayload").doesNotExist()));
        // The amount is the booking's server-computed total, never anything the client sent.
        assertThat(new BigDecimal(payment.path("amount").asText()))
                .isEqualByComparingTo(new BigDecimal(booking.path("totalPrice").asText()));
        long paymentId = payment.path("paymentId").asLong();
        String orderId = payment.path("gatewayOrderId").asText();
        // The seat hold now covers the checkout session (15m) + grace (5m).
        assertThat(jdbc.queryForObject("select hold_expires_at > now() + interval '19' minute from bookings where id = ?",
                Boolean.class, bookingId)).isTrue();

        when(gateway.fetchOrderStatus(orderId)).thenReturn(captured(orderId, "pay_ok1", payment.path("amount").asText()));
        verifyPayment(student, paymentId, orderId, "pay_ok1", "good-signature")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.gatewayOrderId").doesNotExist());
        bookingStatus(student, bookingId, "CONFIRMED");
        assertThat(jdbc.queryForObject("select signature_verified from payment_attempts where gateway_order_id = ?",
                Boolean.class, orderId)).isTrue();

        // A second verify (double tap) and a webhook for the same capture change nothing.
        verifyPayment(student, paymentId, orderId, "pay_ok1", "good-signature")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        deliverWebhook("evt_" + paymentId, orderId, "pay_ok1", GatewayPaymentState.SUCCESS, payment.path("amount").asText())
                .andExpect(status().isOk());
        assertThat(count("select count(*) from payment_audit_events where payment_id = ? and action = 'BOOKING_CONFIRMED'",
                paymentId)).isEqualTo(1);
        assertThat(count("select count(*) from enrollments where user_id = ? and study_hall_id = ?", student, hallId)).isEqualTo(1);

        mvc.perform(get("/api/payments/booking/{id}", bookingId).header("Authorization", TestTokens.bearer(student, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(paymentId));

        // Ledger: exactly one PAYMENT row despite the duplicate verify and webhook.
        assertThat(count("select count(*) from payment_transactions where payment_id = ?", paymentId)).isEqualTo(1);
        String user = TestTokens.bearer(student, "USER");
        JsonNode txns = json(mvc.perform(get("/api/payments/transactions").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("ONLINE"))
                .andExpect(jsonPath("$.content[0].gatewayPaymentId").value("pay_ok1"))
                .andExpect(jsonPath("$.content[0].gatewayOrderId").value(orderId))
                .andExpect(jsonPath("$.content[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$.content[0].transactionReference").value(containsString("TXN-"))));
        long txnId = txns.path("content").get(0).path("id").asLong();
        mvc.perform(get("/api/payments/transactions/{id}", txnId).header("Authorization", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(payment.path("amount").asDouble()));
        mvc.perform(get("/api/payments/transactions/{id}", txnId)
                        .header("Authorization", TestTokens.bearer(IDS.incrementAndGet(), "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payments/history").header("Authorization", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));

        // Booking timeline: created by the student, confirmed by the payment.
        mvc.perform(get("/api/bookings/{id}/history", bookingId).header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.statusHistory[0].fromStatus").value(nullValue()))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$.statusHistory[0].actorType").value("USER"))
                .andExpect(jsonPath("$.statusHistory[1].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.statusHistory[1].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.statusHistory[1].actorType").value("SYSTEM"))
                .andExpect(jsonPath("$.statusHistory[1].paymentId").value(paymentId))
                .andExpect(jsonPath("$.payments[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactions.length()").value(1));
    }

    @Test
    void creationRulesAndIdempotency() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 4).path("id").asLong();

        // Someone else's booking: 404.
        createPayment(IDS.incrementAndGet(), bookingId, null, null).andExpect(status().isNotFound());

        String key = "idem-" + UUID.randomUUID();
        long first = json(createPayment(student, bookingId, null, key).andExpect(status().isCreated())).path("paymentId").asLong();
        // Same key -> same payment; a different key while one is open -> still the same open payment.
        createPayment(student, bookingId, null, key).andExpect(jsonPath("$.paymentId").value(first));
        createPayment(student, bookingId, null, "other-" + UUID.randomUUID()).andExpect(jsonPath("$.paymentId").value(first));
        verify(gateway, times(1)).createOrder(any());
        // Another student cannot replay the key.
        createPayment(IDS.incrementAndGet(), bookingId, null, key).andExpect(status().isNotFound());

        // Booking no longer PENDING.
        long cancelledBooking = book(student, secondSeatId, 24).path("id").asLong();
        send(post("/api/bookings/{id}/cancel", cancelledBooking), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isOk());
        createPayment(student, cancelledBooking, null, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("BOOKING_NOT_PAYABLE"));
    }

    @Test
    void expiredHoldModeGatesAndGatewayProblems() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 5).path("id").asLong();
        jdbc.update("update bookings set hold_expires_at = now() - interval '1' minute where id = ?", bookingId);
        createPayment(student, bookingId, null, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("BOOKING_HOLD_EXPIRED"));
        jdbc.update("update bookings set hold_expires_at = now() + interval '10' minute where id = ?", bookingId);

        when(modeClient.getCurrentMode()).thenReturn(PaymentMode.PAY_AT_HALL_ONLY);
        createPayment(student, bookingId, "ONLINE", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ONLINE_PAYMENT_DISABLED"));
        mvc.perform(get("/api/payments/mode").header("Authorization", TestTokens.bearer(vendorId, "VENDOR")))
                .andExpect(jsonPath("$.paymentMode").value("PAY_AT_HALL_ONLY"));
        when(modeClient.getCurrentMode()).thenReturn(PaymentMode.ONLINE_ONLY);
        createPayment(student, bookingId, "PAY_AT_HALL", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("PAY_AT_HALL_DISABLED"));
        when(modeClient.getCurrentMode()).thenReturn(PaymentMode.BOTH);

        when(gateway.getPublicKeyId()).thenThrow(new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED, "x"));
        createPayment(student, bookingId, null, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PAYMENT_GATEWAY_NOT_CONFIGURED"));
        assertThat(count("select count(*) from payments where booking_id = ?", bookingId)).isZero();

        org.mockito.Mockito.doReturn("rzp_test_public").when(gateway).getPublicKeyId();
        when(gateway.createOrder(any())).thenThrow(new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_ERROR, "down"));
        createPayment(student, bookingId, null, null)
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.error").value("PAYMENT_GATEWAY_ERROR"));
        // Nothing was charged: the payment is FAILED (persisted despite the error) and can be retried.
        assertThat(jdbc.queryForObject("select status from payments where booking_id = ?", String.class, bookingId))
                .isEqualTo("FAILED");
    }

    @Test
    void invalidSignaturePendingGatewayAndAmountMismatch() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 6).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        long paymentId = payment.path("paymentId").asLong();
        String orderId = payment.path("gatewayOrderId").asText();

        verifyPayment(student, paymentId, orderId, "pay_x", "forged")
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("PAYMENT_SIGNATURE_INVALID"));
        // The failed check is recorded although the request failed.
        assertThat(jdbc.queryForObject("select signature_verified from payment_attempts where gateway_order_id = ?",
                Boolean.class, orderId)).isFalse();
        verifyPayment(student, paymentId, "order_unknown", "pay_x", "good-signature")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("PAYMENT_REFERENCE_MISMATCH"));

        // Valid signature, but Razorpay hasn't captured yet: PROCESSING, never SUCCESS on the client's word.
        when(gateway.fetchOrderStatus(orderId)).thenReturn(GatewayStatusResult.builder()
                .state(GatewayPaymentState.PENDING).gatewayOrderId(orderId).gatewayPaymentId("pay_x").build());
        verifyPayment(student, paymentId, orderId, "pay_x", "good-signature")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        bookingStatus(student, bookingId, "PENDING");

        // Captured, but not the amount we asked for: the money goes back, the booking is not confirmed.
        when(gateway.fetchOrderStatus(orderId)).thenReturn(captured(orderId, "pay_x", "1.00"));
        verifyPayment(student, paymentId, orderId, "pay_x", "good-signature")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_PENDING"));
        bookingStatus(student, bookingId, "PENDING");
    }

    // ── Webhook ─────────────────────────────────────────────────────────────

    @Test
    void webhookIsHmacOnlyDedupedAndReprocessedAfterAnError() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 7).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        String orderId = payment.path("gatewayOrderId").asText();
        String amount = payment.path("amount").asText();

        when(gateway.verifyAndParseWebhook(anyString(), any())).thenReturn(
                WebhookVerificationResult.builder().signatureValid(false).build());
        mvc.perform(post("/api/payments/webhook").content("{\"forged\":true}").header("X-Razorpay-Signature", "nope"))
                .andExpect(status().isUnauthorized());

        // Unknown order: recorded, 200 (so Razorpay stops retrying), no effect.
        deliverWebhook("evt_unknown_" + bookingId, "order_not_ours", "pay_z", GatewayPaymentState.SUCCESS, amount)
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select processing_result from payment_webhook_events where dedupe_key = ?",
                String.class, "evt_unknown_" + bookingId)).isEqualTo("IGNORED_UNKNOWN_PAYMENT");

        // A previous delivery of this event failed (ERROR row): the redelivery is processed, not skipped.
        String eventId = "evt_capture_" + bookingId;
        jdbc.update("insert into payment_webhook_events (dedupe_key, event_type, processing_result, received_at) "
                + "values (?, 'payment.captured', 'ERROR: boom', now())", eventId);
        deliverWebhook(eventId, orderId, "pay_w1", GatewayPaymentState.SUCCESS, amount).andExpect(status().isOk());
        bookingStatus(student, bookingId, "CONFIRMED");
        assertThat(jdbc.queryForObject("select processing_result from payment_webhook_events where dedupe_key = ?",
                String.class, eventId)).isEqualTo("PROCESSED");

        // Exact replay: 200, nothing happens.
        deliverWebhook(eventId, orderId, "pay_w1", GatewayPaymentState.SUCCESS, amount).andExpect(status().isOk());
        assertThat(count("select count(*) from payment_audit_events where payment_id = ? and action = 'BOOKING_CONFIRMED'",
                payment.path("paymentId").asLong())).isEqualTo(1);
    }

    // ── Cancel / retry ──────────────────────────────────────────────────────

    @Test
    void cancelAsksTheGatewayFirst() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 8).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        long paymentId = payment.path("paymentId").asLong();
        String orderId = payment.path("gatewayOrderId").asText();

        // Checkout dismissed without paying: nothing at the gateway, cancelled directly.
        when(gateway.fetchOrderStatus(orderId)).thenReturn(GatewayStatusResult.builder()
                .state(GatewayPaymentState.PENDING).gatewayOrderId(orderId).build());
        send(post("/api/payments/{id}/cancel", paymentId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        // Retry opens a NEW attempt with a NEW order.
        JsonNode retried = json(send(post("/api/payments/{id}/retry", paymentId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptNumber").value(2)));
        String secondOrder = retried.path("gatewayOrderId").asText();
        assertThat(secondOrder).isNotEqualTo(orderId);

        // "Dismissed", but the money actually landed: the user's cancel does not win over the gateway.
        when(gateway.fetchOrderStatus(secondOrder)).thenReturn(captured(secondOrder, "pay_c2", payment.path("amount").asText()));
        send(post("/api/payments/{id}/cancel", paymentId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        bookingStatus(student, bookingId, "CONFIRMED");
        send(post("/api/payments/{id}/retry", paymentId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_ALREADY_SUCCESSFUL"));
    }

    @Test
    void retryWhileProcessingIsRefusedAndRetryAfterFailureWorks() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 9).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        long paymentId = payment.path("paymentId").asLong();
        String orderId = payment.path("gatewayOrderId").asText();

        when(gateway.fetchOrderStatus(orderId)).thenReturn(GatewayStatusResult.builder()
                .state(GatewayPaymentState.PENDING).gatewayOrderId(orderId).gatewayPaymentId("pay_auth").build());
        send(post("/api/payments/{id}/retry", paymentId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_ALREADY_PROCESSING"));

        when(gateway.fetchOrderStatus(orderId)).thenReturn(GatewayStatusResult.builder()
                .state(GatewayPaymentState.FAILED).gatewayOrderId(orderId).gatewayPaymentId("pay_auth")
                .errorCode("BAD_REQUEST_ERROR").errorDescription("Payment declined by bank").build());
        send(post("/api/payments/{id}/retry", paymentId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attemptNumber").value(2));
        assertThat(count("select count(*) from payment_attempts where payment_id = ?", paymentId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from payment_attempts where payment_id = ? and attempt_number = 1",
                String.class, paymentId)).isEqualTo("FAILED");
    }

    @Test
    void cancellingAPendingBookingCancelsItsOpenPayment() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 10).path("id").asLong();
        long paymentId = json(createPayment(student, bookingId, "PAY_AT_HALL", null)).path("paymentId").asLong();

        send(post("/api/bookings/{id}/cancel", bookingId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select status from payments where id = ?", String.class, paymentId))
                .isEqualTo("CANCELLED");
    }

    @Test
    void cancellingABookingWhosePaymentActuallyWentThroughKeepsItConfirmed() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 11).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        String orderId = payment.path("gatewayOrderId").asText();
        when(gateway.fetchOrderStatus(orderId)).thenReturn(captured(orderId, "pay_bc", payment.path("amount").asText()));

        send(post("/api/bookings/{id}/cancel", bookingId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_ALREADY_SUCCESSFUL"));
        bookingStatus(student, bookingId, "CONFIRMED");
    }

    // ── Reconciliation, late success, refunds ───────────────────────────────

    @Test
    void reconciliationSweepResolvesOrExpiresStaleSessions() throws Exception {
        long student = IDS.incrementAndGet();
        long paidBooking = book(student, seatId, 12).path("id").asLong();
        JsonNode paid = json(createPayment(student, paidBooking, null, null));
        long otherStudent = IDS.incrementAndGet();
        long quietBooking = book(otherStudent, secondSeatId, 12).path("id").asLong();
        JsonNode quiet = json(createPayment(otherStudent, quietBooking, null, null));
        jdbc.update("update payments set expires_at = now() - interval '1' minute where id in (?, ?)",
                paid.path("paymentId").asLong(), quiet.path("paymentId").asLong());

        when(gateway.fetchOrderStatus(paid.path("gatewayOrderId").asText()))
                .thenReturn(captured(paid.path("gatewayOrderId").asText(), "pay_rec", paid.path("amount").asText()));
        when(gateway.fetchOrderStatus(quiet.path("gatewayOrderId").asText())).thenReturn(GatewayStatusResult.builder()
                .state(GatewayPaymentState.PENDING).gatewayOrderId(quiet.path("gatewayOrderId").asText()).build());

        assertThat(paymentService.findStalePaymentIds())
                .contains(paid.path("paymentId").asLong(), quiet.path("paymentId").asLong());
        paymentService.findStalePaymentIds().forEach(paymentService::reconcileOnePayment);

        assertThat(paymentStatus(paid.path("paymentId").asLong())).isEqualTo("SUCCESS");
        bookingStatus(student, paidBooking, "CONFIRMED");
        // Still uncertain: EXPIRED (not FAILED: the money might still land), and the user may retry.
        assertThat(paymentStatus(quiet.path("paymentId").asLong())).isEqualTo("EXPIRED");
    }

    @Test
    void lateSuccessRevivesAnExpiredBookingWhenTheSeatIsStillFree() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 13).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        jdbc.update("update bookings set status = 'EXPIRED' where id = ?", bookingId);
        jdbc.update("update payments set status = 'EXPIRED' where id = ?", payment.path("paymentId").asLong());

        deliverWebhook("evt_late_" + bookingId, payment.path("gatewayOrderId").asText(), "pay_late",
                GatewayPaymentState.SUCCESS, payment.path("amount").asText()).andExpect(status().isOk());
        assertThat(paymentStatus(payment.path("paymentId").asLong())).isEqualTo("SUCCESS");
        bookingStatus(student, bookingId, "CONFIRMED");
    }

    @Test
    void lateSuccessAfterTheSeatWasTakenIsRefundedExactlyOnce() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 14).path("id").asLong();
        JsonNode payment = json(createPayment(student, bookingId, null, null));
        long paymentId = payment.path("paymentId").asLong();
        jdbc.update("update bookings set status = 'EXPIRED' where id = ?", bookingId);
        long otherStudent = IDS.incrementAndGet();
        book(otherStudent, seatId, 14); // the seat is gone

        deliverWebhook("evt_lost_" + bookingId, payment.path("gatewayOrderId").asText(), "pay_lost",
                GatewayPaymentState.SUCCESS, payment.path("amount").asText()).andExpect(status().isOk());
        assertThat(paymentStatus(paymentId)).isEqualTo("REFUND_PENDING");
        bookingStatus(student, bookingId, "EXPIRED");

        when(gateway.ensureFullRefund(eq("pay_lost"), any(), eq("refund-" + paymentId)))
                .thenReturn(GatewayRefund.builder().gatewayRefundId("rfnd_1").status("processed").build());
        assertThat(paymentService.findRefundPendingIds()).contains(paymentId);
        paymentService.processRefund(paymentId);
        paymentService.processRefund(paymentId); // second sweep: already REFUNDED, no gateway call
        verify(gateway, times(1)).ensureFullRefund(anyString(), any(), anyString());
        assertThat(paymentStatus(paymentId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select gateway_refund_id from payments where id = ?", String.class, paymentId))
                .isEqualTo("rfnd_1");
        mvc.perform(get("/api/payments/{id}", paymentId).header("Authorization", TestTokens.bearer(student, "USER")))
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        // Ledger: the capture and its refund, once each, the refund completed with the gateway's id.
        assertThat(count("select count(*) from payment_transactions where payment_id = ? and type = 'PAYMENT' "
                + "and status = 'SUCCESS' and gateway_payment_id = 'pay_lost'", paymentId)).isEqualTo(1);
        assertThat(count("select count(*) from payment_transactions where payment_id = ? and type = 'REFUND' "
                + "and status = 'SUCCESS' and gateway_refund_id = 'rfnd_1' and completed_at is not null", paymentId))
                .isEqualTo(1);
        assertThat(count("select count(*) from payment_transactions where payment_id = ?", paymentId)).isEqualTo(2);
        mvc.perform(get("/api/payments/transactions").param("type", "REFUND")
                        .header("Authorization", TestTokens.bearer(student, "USER")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].gatewayRefundId").value("rfnd_1"));
    }

    // ── Pay at Hall ─────────────────────────────────────────────────────────

    @Test
    void payAtHallQrPathConfirmsOnlyOnExplicitVendorConfirmation() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 15).path("id").asLong();
        JsonNode created = json(createPayment(student, bookingId, "PAY_AT_HALL", "pah-" + UUID.randomUUID())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.gatewayKeyId").doesNotExist()));
        long paymentId = created.path("paymentId").asLong();
        String qr = created.path("qrPayload").asText();
        String code = created.path("verificationCode").asText();
        assertThat(qr).startsWith("SPACZ-PAY-HALL:");
        assertThat(code).matches("\\d{6}");
        verify(gateway, never()).createOrder(any());

        // The raw QR/code are returned exactly once: never by a read, never stored, never audited.
        mvc.perform(get("/api/payments/{id}", paymentId).header("Authorization", TestTokens.bearer(student, "USER")))
                .andExpect(jsonPath("$.qrPayload").doesNotExist())
                .andExpect(jsonPath("$.verificationCode").doesNotExist());
        assertThat(count("select count(*) from pay_at_hall_verifications where qr_token_hash = ? or verification_code_hash = ?",
                qr.substring("SPACZ-PAY-HALL:".length()), code)).isZero();

        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        mvc.perform(get("/api/payments/pay-at-hall/pending").header("Authorization", vendor))
                .andExpect(jsonPath("$[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$[0].seatNumber").exists());

        // Confirming before verifying is refused.
        send(post("/api/payments/pay-at-hall/{id}/confirm", paymentId), vendor, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_NOT_CONFIRMABLE"));

        // Another vendor's device scanning this QR.
        long otherVendor = IDS.incrementAndGet();
        approvedVendor(otherVendor);
        send(post("/api/payments/pay-at-hall/verify-qr"), TestTokens.bearer(otherVendor, "VENDOR"),
                "{\"qrToken\":\"" + qr + "\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("WRONG_VENDOR"));
        send(post("/api/payments/pay-at-hall/verify-qr"), vendor, "{\"qrToken\":\"SPACZ-PAY-HALL:not-a-real-token\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("INVALID_QR"));

        // Scan: verified, NOT paid.
        send(post("/api/payments/pay-at-hall/verify-qr"), vendor, "{\"qrToken\":\"" + qr + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_VENDOR_CONFIRMATION"));
        bookingStatus(student, bookingId, "PENDING");

        send(post("/api/payments/pay-at-hall/{id}/confirm", paymentId), vendor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.confirmationMethod").value("QR"));
        bookingStatus(student, bookingId, "CONFIRMED");
        // Idempotent: a second tap / a second device.
        send(post("/api/payments/pay-at-hall/{id}/confirm", paymentId), vendor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertThat(count("select count(*) from payment_audit_events where payment_id = ? and action = 'BOOKING_CONFIRMED'",
                paymentId)).isEqualTo(1);
        assertThat(count("select count(*) from payment_audit_events where payment_id = ? and (metadata like ? or metadata like ?)",
                paymentId, "%" + code + "%", "%" + qr.substring(16, 30) + "%")).isZero();

        mvc.perform(get("/api/payments/pay-at-hall/history").header("Authorization", vendor))
                .andExpect(jsonPath("$.content[0].paymentId").value(paymentId));

        // Ledger: one PAY_AT_HALL row, collected by this vendor, despite the second confirm tap.
        assertThat(count("select count(*) from payment_transactions where payment_id = ? and type = 'PAYMENT' "
                + "and payment_method = 'PAY_AT_HALL' and collected_by_vendor_id = ?", paymentId, vendorId)).isEqualTo(1);
        mvc.perform(get("/api/vendors/me/transactions").header("Authorization", vendor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentId").value(paymentId))
                .andExpect(jsonPath("$.content[0].paymentMethod").value("PAY_AT_HALL"));
        mvc.perform(get("/api/vendors/me/transactions").header("Authorization", TestTokens.bearer(otherVendor, "VENDOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        long vendorTxnId = jdbc.queryForObject("select id from payment_transactions where payment_id = ? and type = 'PAYMENT'",
                Long.class, paymentId);
        mvc.perform(get("/api/vendors/me/transactions/{id}", vendorTxnId).header("Authorization", vendor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.collectedByVendorId").value(vendorId));
        mvc.perform(get("/api/vendors/me/transactions/{id}", vendorTxnId)
                        .header("Authorization", TestTokens.bearer(otherVendor, "VENDOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/vendors/me/transactions/summary").header("Authorization", vendor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentCount").value(1))
                .andExpect(jsonPath("$.collected").value(created.path("amount").asDouble()))
                .andExpect(jsonPath("$.collectedByMethod.PAY_AT_HALL").value(created.path("amount").asDouble()))
                .andExpect(jsonPath("$.collectedByMethod.ONLINE").value(0))
                .andExpect(jsonPath("$.net").value(created.path("amount").asDouble()));
        mvc.perform(get("/api/bookings/{id}/history", bookingId).header("Authorization", vendor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[1].toStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.statusHistory[1].actorType").value("VENDOR"))
                .andExpect(jsonPath("$.statusHistory[1].actorId").value(vendorId));
        mvc.perform(get("/api/bookings/{id}/history", bookingId).header("Authorization", TestTokens.bearer(otherVendor, "VENDOR")))
                .andExpect(status().isNotFound());
        internal(get("/internal/payments/{id}/audit-events", paymentId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("PAYMENT_CREATED"));
    }

    @Test
    void wrongCodesLockTheSessionAndTheCounterSurvivesTheRequest() throws Exception {
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 16).path("id").asLong();
        JsonNode created = json(createPayment(student, bookingId, "PAY_AT_HALL", null));
        long paymentId = created.path("paymentId").asLong();
        String code = created.path("verificationCode").asText();
        String wrong = code.equals("000000") ? "111111" : "000000";
        String vendor = TestTokens.bearer(vendorId, "VENDOR");

        for (int i = 1; i <= 4; i++) {
            verifyCode(vendor, paymentId, wrong)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error").value("INVALID_PAYMENT_CODE"))
                    .andExpect(jsonPath("$.message").value(containsString((5 - i) + " attempt")));
            // Persisted although each request failed (would roll back without noRollbackFor).
            assertThat(count("select attempt_count from pay_at_hall_verifications where payment_id = ?", paymentId))
                    .isEqualTo(i);
        }
        verifyCode(vendor, paymentId, wrong).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_ATTEMPTS"));
        assertThat(jdbc.queryForObject("select locked_until > now() from pay_at_hall_verifications where payment_id = ?",
                Boolean.class, paymentId)).isTrue();
        // Even the right code is refused while locked.
        verifyCode(vendor, paymentId, code).andExpect(status().isTooManyRequests());

        jdbc.update("update pay_at_hall_verifications set locked_until = now() - interval '1' second where payment_id = ?",
                paymentId);
        verifyCode(vendor, paymentId, code.substring(0, 3) + " " + code.substring(3))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_VENDOR_CONFIRMATION"));
    }

    @Test
    void payAtHallExpirySeatLossAndVendorSwitch() throws Exception {
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        long student = IDS.incrementAndGet();

        // Expired session: verifying persists EXPIRED.
        long bookingId = book(student, seatId, 17).path("id").asLong();
        JsonNode created = json(createPayment(student, bookingId, "PAY_AT_HALL", null));
        long paymentId = created.path("paymentId").asLong();
        assertThat(jdbc.queryForObject("select expires_at <= now() + interval '4' hour from payments where id = ?",
                Boolean.class, paymentId)).isTrue();
        jdbc.update("update payments set expires_at = now() - interval '1' minute where id = ?", paymentId);
        verifyCode(vendor, paymentId, created.path("verificationCode").asText())
                .andExpect(status().isGone()).andExpect(jsonPath("$.error").value("PAYMENT_EXPIRED"));
        assertThat(paymentStatus(paymentId)).isEqualTo("EXPIRED");

        // Expiry sweep for sessions nobody touched.
        long student2 = IDS.incrementAndGet();
        long booking2 = book(student2, secondSeatId, 17).path("id").asLong();
        long payment2 = json(createPayment(student2, booking2, "PAY_AT_HALL", null)).path("paymentId").asLong();
        jdbc.update("update payments set expires_at = now() - interval '1' minute where id = ?", payment2);
        assertThat(paymentService.findStalePayAtHallIds()).contains(payment2);
        paymentService.expirePayAtHallSession(payment2);
        assertThat(paymentStatus(payment2)).isEqualTo("EXPIRED");

        // Booking no longer valid when the vendor confirms: refused, session closed, nothing to collect.
        long student3 = IDS.incrementAndGet();
        long booking3 = book(student3, seatId, 18).path("id").asLong();
        JsonNode created3 = json(createPayment(student3, booking3, "PAY_AT_HALL", null));
        long payment3 = created3.path("paymentId").asLong();
        send(post("/api/payments/pay-at-hall/verify-qr"), vendor,
                "{\"qrToken\":\"" + created3.path("qrPayload").asText() + "\"}").andExpect(status().isOk());
        jdbc.update("update bookings set status = 'CANCELLED' where id = ?", booking3);
        send(post("/api/payments/pay-at-hall/{id}/confirm", payment3), vendor, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PAYMENT_NOT_CONFIRMABLE"))
                .andExpect(jsonPath("$.message").value(containsString("Do not collect")));
        assertThat(paymentStatus(payment3)).isEqualTo("CANCELLED");

        // Admin switched Pay at Hall off for this vendor.
        internal(patch("/internal/vendors/{id}/pay-at-hall", vendorId), "{\"enabled\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.payAtHallEnabled").value(false));
        long booking4 = book(IDS.incrementAndGet(), secondSeatId, 19).path("id").asLong();
        long student4 = jdbc.queryForObject("select user_id from bookings where id = ?", Long.class, booking4);
        createPayment(student4, booking4, "PAY_AT_HALL", null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("PAY_AT_HALL_DISABLED"));
        createPayment(student4, booking4, "ONLINE", null).andExpect(status().isCreated());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        String user = TestTokens.bearer(IDS.incrementAndGet(), "USER");
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        send(post("/api/payments/pay-at-hall/verify-qr"), user, "{\"qrToken\":\"x\"}").andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/pay-at-hall/pending").header("Authorization", user)).andExpect(status().isForbidden());
        send(post("/api/payments"), vendor, "{\"bookingId\":1}").andExpect(status().isForbidden());
        send(post("/api/payments"), null, "{\"bookingId\":1}").andExpect(status().isUnauthorized());
        // The free self-confirm is gone: a student can't confirm a booking without paying.
        long student = IDS.incrementAndGet();
        long bookingId = book(student, seatId, 20).path("id").asLong();
        send(post("/api/bookings/{id}/confirm", bookingId), TestTokens.bearer(student, "USER"), null)
                .andExpect(status().isForbidden());
        bookingStatus(student, bookingId, "PENDING");
    }

    @Test
    void bookingHistoryRecordsExpiryCancellationAndAdminViews() throws Exception {
        // A stale hold released by someone else's booking of the same seat is recorded as a SYSTEM expiry.
        long student = IDS.incrementAndGet();
        long stale = book(student, secondSeatId, 21).path("id").asLong();
        jdbc.update("update bookings set hold_expires_at = now() - interval '1' minute where id = ?", stale);
        long other = IDS.incrementAndGet();
        long taker = book(other, secondSeatId, 21).path("id").asLong();
        bookingStatus(student, stale, "EXPIRED");
        assertThat(count("select count(*) from booking_status_history where booking_id = ? and from_status = 'PENDING' "
                + "and to_status = 'EXPIRED' and actor_type = 'SYSTEM'", stale)).isEqualTo(1);

        // The student who cancels is recorded with their reason.
        send(post("/api/bookings/{id}/cancel", taker), TestTokens.bearer(other, "USER"), "{\"reason\":\"exam moved\"}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/bookings/{id}/history", taker).header("Authorization", TestTokens.bearer(other, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistory[1].actorType").value("USER"))
                .andExpect(jsonPath("$.statusHistory[1].actorId").value(other))
                .andExpect(jsonPath("$.statusHistory[1].reason").value(containsString("exam moved")))
                .andExpect(jsonPath("$.transactions.length()").value(0));
        // Not visible to another student.
        mvc.perform(get("/api/bookings/{id}/history", taker).header("Authorization", TestTokens.bearer(student, "USER")))
                .andExpect(status().isNotFound());

        // Admin manual confirmation is recorded with the admin's id.
        long third = IDS.incrementAndGet();
        long manual = book(third, seatId, 22).path("id").asLong();
        long adminId = IDS.incrementAndGet();
        send(post("/api/bookings/{id}/confirm", manual), TestTokens.bearer(adminId, "ADMIN"), null).andExpect(status().isOk());
        internal(get("/internal/bookings/{id}/history", manual), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[1].actorType").value("ADMIN"))
                .andExpect(jsonPath("$.statusHistory[1].actorId").value(adminId));

        // Admin ledger list and summary, filtered by vendor; a reversed date range is rejected.
        long payer = IDS.incrementAndGet();
        long paid = book(payer, seatId, 23).path("id").asLong();
        JsonNode payment = json(createPayment(payer, paid, null, null));
        String orderId = payment.path("gatewayOrderId").asText();
        when(gateway.fetchOrderStatus(orderId)).thenReturn(captured(orderId, "pay_adm", payment.path("amount").asText()));
        verifyPayment(payer, payment.path("paymentId").asLong(), orderId, "pay_adm", "good-signature")
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        internal(get("/internal/transactions").param("vendorId", String.valueOf(vendorId)).param("userId", String.valueOf(payer)), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].gatewayPaymentId").value("pay_adm"));
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        internal(get("/internal/transactions/summary").param("vendorId", String.valueOf(vendorId))
                .param("from", today).param("to", today), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.collectedByMethod.ONLINE").value(org.hamcrest.Matchers.greaterThan(0.0)));
        internal(get("/internal/transactions").param("from", "2026-02-02").param("to", "2026-02-01"), null)
                .andExpect(status().isUnprocessableEntity());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private JsonNode book(long student, long seat, int daysAhead) throws Exception {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(daysAhead);
        return json(send(post("/api/bookings"), TestTokens.bearer(student, "USER"),
                "{\"studyHallId\":" + hallId + ",\"seatId\":" + seat + ",\"startDate\":\"" + day + "\",\"endDate\":\"" + day + "\"}")
                .andExpect(status().isCreated()));
    }

    private ResultActions createPayment(long student, long bookingId, String method, String idempotencyKey) throws Exception {
        var request = post("/api/payments");
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(request, TestTokens.bearer(student, "USER"), "{\"bookingId\":" + bookingId
                + (method != null ? ",\"paymentMethod\":\"" + method + "\"" : "") + "}");
    }

    private ResultActions verifyPayment(long student, long paymentId, String orderId, String gatewayPaymentId,
                                        String signature) throws Exception {
        return send(post("/api/payments/{id}/verify", paymentId), TestTokens.bearer(student, "USER"),
                "{\"gatewayOrderId\":\"" + orderId + "\",\"gatewayPaymentId\":\"" + gatewayPaymentId
                        + "\",\"gatewaySignature\":\"" + signature + "\"}");
    }

    private ResultActions verifyCode(String vendor, long paymentId, String code) throws Exception {
        return send(post("/api/payments/pay-at-hall/verify-code"), vendor,
                "{\"paymentId\":" + paymentId + ",\"code\":\"" + code + "\"}");
    }

    private ResultActions deliverWebhook(String eventId, String orderId, String gatewayPaymentId,
                                         GatewayPaymentState state, String amount) throws Exception {
        String raw = "{\"id\":\"" + eventId + "\",\"event\":\"payment.captured\"}";
        when(gateway.verifyAndParseWebhook(eq(raw), eq("sig-" + eventId))).thenReturn(WebhookVerificationResult.builder()
                .signatureValid(true).gatewayEventId(eventId).eventType("payment.captured").rawJson(raw)
                .status(GatewayStatusResult.builder().state(state).gatewayOrderId(orderId)
                        .gatewayPaymentId(gatewayPaymentId).amount(new BigDecimal(amount)).build())
                .build());
        // No Authorization header: Razorpay's servers have no SPACZ token.
        return mvc.perform(post("/api/payments/webhook").content(raw).header("X-Razorpay-Signature", "sig-" + eventId));
    }

    private static GatewayStatusResult captured(String orderId, String gatewayPaymentId, String amount) {
        return GatewayStatusResult.builder().state(GatewayPaymentState.SUCCESS).gatewayOrderId(orderId)
                .gatewayPaymentId(gatewayPaymentId).amount(new BigDecimal(amount)).currency("INR").build();
    }

    private void bookingStatus(long student, long bookingId, String expected) throws Exception {
        mvc.perform(get("/api/bookings/{id}", bookingId).header("Authorization", TestTokens.bearer(student, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(expected));
    }

    private String paymentStatus(long paymentId) {
        return jdbc.queryForObject("select status from payments where id = ?", String.class, paymentId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
