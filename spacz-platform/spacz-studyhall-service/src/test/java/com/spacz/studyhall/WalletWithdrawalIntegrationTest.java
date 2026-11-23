package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.payment.enums.PaymentMode;
import com.spacz.studyhall.payment.gateway.CreateOrderCommand;
import com.spacz.studyhall.payment.gateway.GatewayOrder;
import com.spacz.studyhall.payment.gateway.GatewayPaymentState;
import com.spacz.studyhall.payment.gateway.GatewayStatusResult;
import com.spacz.studyhall.payment.gateway.PaymentGateway;
import com.spacz.studyhall.payment.gateway.PaymentModeConfigClient;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partner wallet end to end: earnings from online payments (with a 10% commission), Pay at Hall dues, payout
 * accounts, and the withdrawal lifecycle (request, cancel, processing, paid, rejected) through the vendor API
 * and the internal admin API.
 */
@SpringBootTest(properties = {"spacz.wallet.commission-percent=10", "spacz.wallet.settlement-hold=48h"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalletWithdrawalIntegrationTest extends ApiTestSupport {

    private static final AtomicLong IDS = new AtomicLong(97_000);

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private PaymentGateway gateway;
    @MockitoBean
    private PaymentModeConfigClient modeClient;

    @Autowired
    private JdbcTemplate jdbc;

    private long vendorId;
    private String vendor;
    private long hallId;
    private long seatId;

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
        vendor = TestTokens.bearer(vendorId, "VENDOR");
        hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Wallet Hall %d","addressLine":"Road 1","city":"Hyderabad","state":"Telangana",
                 "pricePerDay":120.00,"openingTime":"06:00","closingTime":"22:00"}""".formatted(vendorId))
                .andExpect(status().isCreated()), "id");
        JsonNode block = json(send(post("/api/studyhalls/{id}/blocks", hallId), vendor,
                "{\"name\":\"Main\",\"totalRows\":1,\"totalColumns\":2,\"dailyPrice\":120.00}").andExpect(status().isCreated()));
        seatId = block.path("seats").get(0).path("id").asLong();
        send(post("/api/studyhalls/{id}/submit", hallId), vendor, null).andExpect(status().isOk());
        internal(patch("/internal/studyhalls/{id}/status", hallId), "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
    }

    @Test
    void onlineEarningsBecomeWithdrawableAfterTheHoldAndAWithdrawalIsPaidOut() throws Exception {
        // Empty wallet.
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0.0))
                .andExpect(jsonPath("$.canWithdraw").value(false))
                .andExpect(jsonPath("$.withdrawBlockedReason").value("NO_PAYOUT_ACCOUNT"));

        payOnline(3);
        // 120.00 seat price - 10% commission = 108.00, pending for 48h.
        assertThat(count("select count(*) from vendor_wallet_entries where vendor_id = ? and type = 'BOOKING_EARNING'"
                + " and amount = 108.00", vendorId)).isEqualTo(1);
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(0.0))
                .andExpect(jsonPath("$.pendingBalance").value(108.0))
                .andExpect(jsonPath("$.totalEarned").value(108.0))
                .andExpect(jsonPath("$.settlementHoldHours").value(48));
        mvc.perform(get("/api/vendors/me/wallet/entries").header("Authorization", vendor))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_EARNING"))
                .andExpect(jsonPath("$.content[0].pending").value(true));

        // Payout accounts: validation, then a UPI ID and a bank account.
        send(post("/api/vendors/me/payout-accounts"), vendor, "{\"method\":\"UPI\",\"upiId\":\"not-a-upi\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("INVALID_PAYOUT_ACCOUNT"));
        send(post("/api/vendors/me/payout-accounts"), vendor, """
                {"method":"BANK_ACCOUNT","accountHolderName":"Focus Hall","accountNumber":"123","ifscCode":"HDFC0001234"}""")
                .andExpect(status().isUnprocessableEntity());
        long upiId = id(send(post("/api/vendors/me/payout-accounts"), vendor,
                "{\"method\":\"UPI\",\"upiId\":\"Focus.Hall@OKAXIS\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upiId").value("focus.hall@okaxis"))
                .andExpect(jsonPath("$.isDefault").value(true)), "id");
        send(post("/api/vendors/me/payout-accounts"), vendor, "{\"method\":\"UPI\",\"upiId\":\"focus.hall@okaxis\"}")
                .andExpect(status().isConflict());
        long bankId = id(send(post("/api/vendors/me/payout-accounts"), vendor, """
                {"method":"BANK_ACCOUNT","accountHolderName":"Focus Hall","accountNumber":"50100123456789",
                 "ifscCode":"hdfc0001234","bankName":"HDFC Bank","makeDefault":true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumberMasked").value("XXXXXXXXXX6789"))
                .andExpect(jsonPath("$.ifscCode").value("HDFC0001234"))
                .andExpect(jsonPath("$.displayName").value("HDFC Bank ••6789"))
                .andExpect(jsonPath("$.isDefault").value(true)), "id");
        mvc.perform(get("/api/vendors/me/payout-accounts").header("Authorization", vendor))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(bankId))
                .andExpect(jsonPath("$[1].isDefault").value(false));

        // Still in the hold: nothing to withdraw.
        withdraw(100, upiId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));

        settleAll();
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(108.0))
                .andExpect(jsonPath("$.pendingBalance").value(0.0))
                .andExpect(jsonPath("$.canWithdraw").value(true));

        withdraw(50, upiId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("WITHDRAWAL_BELOW_MINIMUM"));
        withdraw(108.01, upiId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));

        // Request -> cancel -> balance restored.
        long first = id(withdraw(100, upiId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.payoutMethod").value("UPI"))
                .andExpect(jsonPath("$.upiId").value("focus.hall@okaxis")), "id");
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(8.0))
                .andExpect(jsonPath("$.inWithdrawal").value(100.0))
                .andExpect(jsonPath("$.withdrawBlockedReason").value("WITHDRAWAL_IN_PROGRESS"))
                .andExpect(jsonPath("$.openWithdrawal.id").value(first));
        withdraw(100, bankId).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("WITHDRAWAL_IN_PROGRESS"));
        // Another vendor cannot see or cancel it.
        String stranger = TestTokens.bearer(IDS.incrementAndGet(), "VENDOR");
        send(post("/api/vendors/me/withdrawals/{id}/cancel", first), stranger, null).andExpect(status().isNotFound());
        mvc.perform(get("/api/vendors/me/withdrawals/{id}", first).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        send(post("/api/vendors/me/withdrawals/{id}/cancel", first), vendor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(108.0));

        // Request to the bank account -> processing (no more cancel) -> paid.
        long second = id(withdraw(108, bankId).andExpect(status().isCreated()), "id");
        internal(post("/internal/withdrawals/{id}/processing", second), "{\"adminId\":1,\"note\":\"NEFT today\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        send(post("/api/vendors/me/withdrawals/{id}/cancel", second), vendor, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("WITHDRAWAL_STATUS_CONFLICT"));
        internal(get("/internal/withdrawals").param("status", "PROCESSING").param("vendorId", String.valueOf(vendorId)), null)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accountNumber").value("50100123456789"))
                .andExpect(jsonPath("$.content[0].vendorBusinessName").value("Focus " + vendorId));
        internal(post("/internal/withdrawals/{id}/paid", second), "{\"adminId\":1}").andExpect(status().isBadRequest());
        internal(post("/internal/withdrawals/{id}/paid", second), "{\"adminId\":1,\"payoutReference\":\"UTR123456789\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payoutReference").value("UTR123456789"));
        internal(post("/internal/withdrawals/{id}/reject", second), "{\"adminId\":1,\"reason\":\"late\"}")
                .andExpect(status().isConflict());

        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(0.0))
                .andExpect(jsonPath("$.inWithdrawal").value(0.0))
                .andExpect(jsonPath("$.totalWithdrawn").value(108.0));
        mvc.perform(get("/api/vendors/me/withdrawals").header("Authorization", vendor))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.content[0].accountNumberMasked").value("XXXXXXXXXX6789"))
                .andExpect(jsonPath("$.content[0].accountNumber").doesNotExist());
        // Deleting the account later does not touch the paid withdrawal's snapshot.
        send(delete("/api/vendors/me/payout-accounts/{id}", bankId), vendor, null).andExpect(status().isNoContent());
        mvc.perform(get("/api/vendors/me/payout-accounts").header("Authorization", vendor))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isDefault").value(true));
        mvc.perform(get("/api/vendors/me/withdrawals/{id}", second).header("Authorization", vendor))
                .andExpect(jsonPath("$.bankName").value("HDFC Bank"));
    }

    @Test
    void rejectedWithdrawalReturnsTheMoneyAndPayAtHallCommissionIsDeducted() throws Exception {
        payOnline(4);
        payOnline(5);
        settleAll();
        long upiId = id(send(post("/api/vendors/me/payout-accounts"), vendor,
                "{\"method\":\"UPI\",\"upiId\":\"hall@ybl\"}").andExpect(status().isCreated()), "id");

        long w = id(withdraw(200, upiId).andExpect(status().isCreated()), "id");
        internal(post("/internal/withdrawals/{id}/reject", w), "{\"adminId\":1,\"reason\":\"UPI name mismatch\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("UPI name mismatch"));
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(216.0));
        assertThat(count("select count(*) from vendor_wallet_entries where withdrawal_id = ? and type = 'WITHDRAWAL_REVERSAL'"
                + " and amount = 200.00", w)).isEqualTo(1);

        // Pay at Hall: the vendor kept the whole 120.00 in cash, so the 12.00 commission is taken from the wallet.
        payAtHall(6);
        assertThat(count("select count(*) from vendor_wallet_entries where vendor_id = ? and type = 'PAY_AT_HALL_FEE'"
                + " and amount = -12.00", vendorId)).isEqualTo(1);
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(204.0))
                .andExpect(jsonPath("$.totalEarned").value(216.0));

        // Admin views.
        internal(get("/internal/vendors/{id}/wallet", vendorId), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableBalance").value(204.0));
        internal(get("/internal/vendors/{id}/wallet/entries", vendorId).param("type", "PAY_AT_HALL_FEE"), null)
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void suspendedVendorCannotWithdraw() throws Exception {
        payOnline(7);
        settleAll();
        long upiId = id(send(post("/api/vendors/me/payout-accounts"), vendor,
                "{\"method\":\"UPI\",\"upiId\":\"hall@ybl\"}").andExpect(status().isCreated()), "id");
        internal(patch("/internal/vendors/{id}/status", vendorId), "{\"action\":\"SUSPEND\",\"reason\":\"KYC\"}")
                .andExpect(status().isOk());
        withdraw(100, upiId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VENDOR_NOT_APPROVED"));
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", vendor))
                .andExpect(jsonPath("$.availableBalance").value(108.0))
                .andExpect(jsonPath("$.withdrawBlockedReason").value("VENDOR_NOT_APPROVED"));
        // Students and admins tokens cannot use the vendor wallet API.
        mvc.perform(get("/api/vendors/me/wallet").header("Authorization", TestTokens.bearer(IDS.incrementAndGet(), "USER")))
                .andExpect(status().isForbidden());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions withdraw(double amount, long accountId) throws Exception {
        return send(post("/api/vendors/me/withdrawals"), vendor,
                "{\"amount\":" + amount + ",\"payoutAccountId\":" + accountId + ",\"note\":\"weekly\"}");
    }

    /** Books one day `daysAhead` from today and pays it online (Razorpay mocked). */
    private void payOnline(int daysAhead) throws Exception {
        long student = IDS.incrementAndGet();
        String user = TestTokens.bearer(student, "USER");
        long bookingId = book(user, daysAhead);
        JsonNode payment = json(send(post("/api/payments").header("Idempotency-Key", "k-" + UUID.randomUUID()), user,
                "{\"bookingId\":" + bookingId + "}").andExpect(status().isCreated()));
        String orderId = payment.path("gatewayOrderId").asText();
        String gatewayPaymentId = "pay_" + UUID.randomUUID().toString().substring(0, 8);
        when(gateway.fetchOrderStatus(orderId)).thenReturn(GatewayStatusResult.builder().state(GatewayPaymentState.SUCCESS)
                .gatewayOrderId(orderId).gatewayPaymentId(gatewayPaymentId)
                .amount(new BigDecimal(payment.path("amount").asText())).currency("INR").build());
        send(post("/api/payments/{id}/verify", payment.path("paymentId").asLong()), user,
                "{\"gatewayOrderId\":\"" + orderId + "\",\"gatewayPaymentId\":\"" + gatewayPaymentId
                        + "\",\"gatewaySignature\":\"good-signature\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    private void payAtHall(int daysAhead) throws Exception {
        String user = TestTokens.bearer(IDS.incrementAndGet(), "USER");
        long bookingId = book(user, daysAhead);
        JsonNode created = json(send(post("/api/payments"), user,
                "{\"bookingId\":" + bookingId + ",\"paymentMethod\":\"PAY_AT_HALL\"}").andExpect(status().isCreated()));
        long paymentId = created.path("paymentId").asLong();
        send(post("/api/payments/pay-at-hall/verify-code"), vendor,
                "{\"paymentId\":" + paymentId + ",\"code\":\"" + created.path("verificationCode").asText() + "\"}")
                .andExpect(status().isOk());
        send(post("/api/payments/pay-at-hall/{id}/confirm", paymentId), vendor, null).andExpect(status().isOk());
    }

    private long book(String user, int daysAhead) throws Exception {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(daysAhead);
        return id(send(post("/api/bookings"), user, "{\"studyHallId\":" + hallId + ",\"seatId\":" + seatId
                + ",\"startDate\":\"" + day + "\",\"endDate\":\"" + day + "\"}").andExpect(status().isCreated()), "id");
    }

    /** Ends the settlement hold of everything in this vendor's wallet. */
    private void settleAll() {
        jdbc.update("update vendor_wallet_entries set available_at = ? where vendor_id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), vendorId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
