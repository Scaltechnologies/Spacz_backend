package com.spacz.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.UserServiceClient;
import com.spacz.admin.paymentgateway.entity.PaymentGatewayConfig;
import com.spacz.admin.paymentgateway.repository.PaymentGatewayConfigRepository;
import com.spacz.admin.paymentgateway.service.SecretEncryptionService;
import com.spacz.admin.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Encrypted gateway credentials, the platform payment mode and the Pay-at-Hall vendor switch. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentSettingsApiTest {

    private static final String ADMIN = TestTokens.bearer(1, "ADMIN");
    private static final String INTERNAL_KEY = "test-internal-api-key-0123456789";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SecretEncryptionService encryption;
    @Autowired
    private PaymentGatewayConfigRepository repository;

    @MockitoBean
    private AuthServiceClient authClient;
    @MockitoBean
    private UserServiceClient userClient;
    @MockitoBean
    private StudyHallServiceClient studyHallClient;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void encryptionRoundTripsAndNeverRepeatsCiphertext() {
        String a = encryption.encrypt("secret-value-1234");
        String b = encryption.encrypt("secret-value-1234");
        assertThat(a).isNotEqualTo(b).doesNotContain("secret-value");
        assertThat(encryption.decrypt(a)).isEqualTo("secret-value-1234");
        assertThat(encryption.decrypt(b)).isEqualTo("secret-value-1234");
        assertThat(encryption.mask("abcdefgh1234")).isEqualTo("•".repeat(8) + "1234");
        assertThat(encryption.mask("abc")).isEqualTo("abc");
    }

    @Test
    void encryptionKeyMustBe32Bytes() {
        assertThatThrownBy(() -> new SecretEncryptionService("").encrypt("x"))
                .hasMessageContaining("ADMIN_SECRETS_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new SecretEncryptionService("c2hvcnQ=").encrypt("x")) // "short"
                .hasMessageContaining("32 bytes");
    }

    @Test
    void firstConfigurationRequiresAKeySecret() throws Exception {
        mvc.perform(put("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("keyId", "rzp_test_abc"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("GATEWAY_SECRET_REQUIRED"));
        mvc.perform(get("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void secretsAreStoredEncryptedReturnedMaskedAndKeptWhenLeftBlank() throws Exception {
        mvc.perform(put("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("keyId", "rzp_test_abc",
                                "keySecret", "key-secret-9999", "webhookSecret", "hook-secret-7777", "mode", "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("RAZORPAY"))
                .andExpect(jsonPath("$.mode").value("TEST"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.keySecretMasked").value(org.hamcrest.Matchers.endsWith("9999")))
                .andExpect(jsonPath("$.webhookSecretConfigured").value(true))
                .andExpect(content().string(not(containsString("key-secret-9999"))))
                .andExpect(content().string(not(containsString("hook-secret-7777"))));

        PaymentGatewayConfig stored = repository.findByProvider("RAZORPAY").orElseThrow();
        String ciphertext = stored.getKeySecretEncrypted();
        assertThat(ciphertext).doesNotContain("key-secret");

        // Update with blank secrets: ciphertext unchanged.
        mvc.perform(put("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyId", "rzp_test_xyz", "keySecret", "", "mode", "LIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_xyz"))
                .andExpect(jsonPath("$.mode").value("LIVE"));
        assertThat(repository.findByProvider("RAZORPAY").orElseThrow().getKeySecretEncrypted()).isEqualTo(ciphertext);

        mvc.perform(get("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("key-secret-9999"))));

        // Internal endpoint: decrypted, internal key only.
        mvc.perform(get("/internal/payment-gateway/RAZORPAY/active"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/payment-gateway/RAZORPAY/active").header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_xyz"))
                .andExpect(jsonPath("$.keySecret").value("key-secret-9999"))
                .andExpect(jsonPath("$.webhookSecret").value("hook-secret-7777"));
    }

    @Test
    void invalidModeIsRejectedAndInactiveConfigCountsAsUnconfigured() throws Exception {
        mvc.perform(put("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyId", "rzp_test_abc", "keySecret", "s", "mode", "PROD"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_GATEWAY_MODE"));

        mvc.perform(put("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyId", "rzp_test_abc", "keySecret", "s", "isActive", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/internal/payment-gateway/razorpay/active").header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void gatewaySettingsAreAdminOnly() throws Exception {
        mvc.perform(get("/api/admin/payment-gateway/razorpay").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/settings/payment-mode").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(6, "VENDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentModeIsValidatedPersistedAndServedInternally() throws Exception {
        mvc.perform(patch("/api/admin/settings/payment-mode").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentMode", "CASH"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_PAYMENT_MODE"));

        mvc.perform(patch("/api/admin/settings/payment-mode").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentMode", "PAY_AT_HALL_ONLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMode").value("PAY_AT_HALL_ONLY"));
        mvc.perform(get("/api/admin/settings/payment-mode").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.paymentMode").value("PAY_AT_HALL_ONLY"));
        mvc.perform(get("/internal/settings/PAYMENT_MODE").header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(jsonPath("$.value").value("PAY_AT_HALL_ONLY"));
        mvc.perform(get("/internal/settings/NOT_A_SETTING").header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));

        mvc.perform(get("/api/admin/audit-logs").param("action", "PAYMENT_MODE_CHANGED")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.content[0].description").value(containsString("-> PAY_AT_HALL_ONLY")));

        // restore for other tests sharing the context
        mvc.perform(patch("/api/admin/settings/payment-mode").header(HttpHeaders.AUTHORIZATION, ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentMode", "BOTH"))));
    }

    @Test
    void payAtHallSwitchIsForwardedAndAudited() throws Exception {
        org.mockito.Mockito.when(studyHallClient.setPayAtHall(300L, false)).thenReturn(objectMapper.readTree(
                "{\"vendorId\":300,\"payAtHallEnabled\":false}"));
        mvc.perform(patch("/api/admin/vendors/300/pay-at-hall").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payAtHallEnabled").value(false));
        verify(studyHallClient).setPayAtHall(300L, false);
        mvc.perform(get("/api/admin/audit-logs").param("action", "VENDOR_PAY_AT_HALL_UPDATED")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.content[0].entityId").value(300));
    }

    @Test
    void transactionLedgerAndBookingHistoryArePassedThrough() throws Exception {
        var query = new com.spacz.admin.client.StudyHallServiceClient.TransactionQuery(null, 300L, null, null,
                "REFUND", null, null, java.time.LocalDate.parse("2026-11-01"), java.time.LocalDate.parse("2026-11-30"));
        org.mockito.Mockito.when(studyHallClient.transactions(org.mockito.ArgumentMatchers.eq(query),
                org.mockito.ArgumentMatchers.any())).thenReturn(objectMapper.readTree(
                "{\"content\":[{\"transactionReference\":\"TXN-ABC\",\"type\":\"REFUND\"}],\"totalElements\":1}"));
        mvc.perform(get("/api/admin/transactions").param("vendorId", "300").param("type", "REFUND")
                        .param("from", "2026-11-01").param("to", "2026-11-30")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionReference").value("TXN-ABC"));

        org.mockito.Mockito.when(studyHallClient.bookingHistory(77L)).thenReturn(objectMapper.readTree(
                "{\"bookingId\":77,\"statusHistory\":[{\"toStatus\":\"PENDING\"}]}"));
        mvc.perform(get("/api/admin/bookings/77/history").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("PENDING"));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
