package com.spacz.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.auth.client.AdminServiceClient;
import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.client.dto.CreateUserProfileRequest;
import com.spacz.auth.client.dto.CreateVendorProfileRequest;
import com.spacz.auth.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phone-OTP login/registration and the single /register endpoint. The OTP code is exposed in the
 * response for these tests only (spacz.otp.expose-code-in-response=true).
 */
@SpringBootTest(properties = "spacz.otp.expose-code-in-response=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OtpAndRegistrationIntegrationTest {

    private static final AtomicLong PHONES = new AtomicLong(9_700_000_000L);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private StudyHallServiceClient studyHallServiceClient;
    @MockitoBean
    private AdminServiceClient adminServiceClient;

    @Test
    void newPhoneRegistersAsStudentWithOtpAndLogsInAgainLater() throws Exception {
        String phone = String.valueOf(PHONES.incrementAndGet());
        String code = requestCode(phone);

        // Unknown number without a role: the code is NOT consumed, the client retries with a role.
        verifyCode(phone, code, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("REGISTRATION_REQUIRED"));
        JsonNode auth = json(verifyCode(phone, code, "\"role\":\"USER\",\"firstName\":\"Asha\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.phone").value("+91" + phone))
                .andExpect(jsonPath("$.user.email").value(org.hamcrest.Matchers.nullValue())));
        ArgumentCaptor<CreateUserProfileRequest> profile = ArgumentCaptor.forClass(CreateUserProfileRequest.class);
        verify(userServiceClient).createProfile(profile.capture());
        assertThat(profile.getValue().phone()).isEqualTo("+91" + phone);
        assertThat(profile.getValue().email()).isNull();

        // The code is single use.
        verifyCode(phone, code, null).andExpect(status().isUnauthorized());

        // The token works without an email claim.
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSet").value(false));

        // Setting a password needs no current password for OTP accounts; then phone + password login works.
        mvc.perform(put("/api/auth/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.path("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Secret123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"0" + phone + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void vendorRegistersWithOtpAndGetsADraftProfile() throws Exception {
        String phone = String.valueOf(PHONES.incrementAndGet());
        String code = requestCode(phone);
        verifyCode(phone, code, "\"role\":\"VENDOR\",\"businessName\":\"Focus Hall\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("VENDOR"));
        ArgumentCaptor<CreateVendorProfileRequest> vendor = ArgumentCaptor.forClass(CreateVendorProfileRequest.class);
        verify(studyHallServiceClient).createVendorProfile(vendor.capture());
        assertThat(vendor.getValue().businessName()).isEqualTo("Focus Hall");
        assertThat(vendor.getValue().phone()).isEqualTo("+91" + phone);
    }

    @Test
    void wrongCodesAreCountedAndTheCodeDiesAfterMaxAttempts() throws Exception {
        String phone = String.valueOf(PHONES.incrementAndGet());
        String code = requestCode(phone);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            verifyCode(phone, wrong, "\"role\":\"USER\",\"firstName\":\"X\"").andExpect(status().isUnauthorized());
        }
        verifyCode(phone, code, "\"role\":\"USER\",\"firstName\":\"X\"").andExpect(status().isUnauthorized());
    }

    @Test
    void codesCannotBeRequestedTooOftenAndAdminCannotRegister() throws Exception {
        String phone = String.valueOf(PHONES.incrementAndGet());
        requestCode(phone);
        mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("OTP_TOO_SOON"));
        mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PHONE"));

        String other = String.valueOf(PHONES.incrementAndGet());
        verifyCode(other, requestCode(other), "\"role\":\"ADMIN\"").andExpect(status().isForbidden());
    }

    @Test
    void singleRegisterEndpointValidatesPerRole() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VENDOR\",\"email\":\"v1@example.com\",\"password\":\"Secret123\",\"businessName\":\"B\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"email\":\"a1@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"email\":\"u1@example.com\",\"password\":\"Secret123\",\"firstName\":\"U\",\"phone\":\"9811111111\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.phone").value("+919811111111"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"email\":\"u2@example.com\",\"password\":\"Secret123\",\"firstName\":\"U\",\"phone\":\"+91 98111 11111\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void legacyAccountsAreImportedIdempotently() throws Exception {
        String body = "{\"legacyLoginId\":77,\"phone\":\"9822222222\",\"role\":\"VENDOR\"}";
        long first = json(mvc.perform(post("/internal/accounts/import").header("X-Internal-Api-Key", TestTokens.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))).path("accountId").asLong();
        mvc.perform(post("/internal/accounts/import").header("X-Internal-Api-Key", TestTokens.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.accountId").value(first));
        // The migrated vendor logs in with an OTP to the same number.
        verifyCode("9822222222", requestCode("9822222222"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(first));
    }

    private String requestCode(String phone) throws Exception {
        return json(mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))).path("devCode").asText();
    }

    private ResultActions verifyCode(String phone, String code, String extra) throws Exception {
        String body = "{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"" + (extra == null ? "" : "," + extra) + "}";
        return mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
