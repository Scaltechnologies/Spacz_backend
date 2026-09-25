package com.spacz.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration → login → JWT validation → refresh rotation → account suspension, against the real
 * security configuration (H2 instead of PostgreSQL; downstream services mocked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private StudyHallServiceClient studyHallServiceClient;

    @Test
    void userRegistersLogsInAndUsesTheToken() throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register/user").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"flow@example.com","password":"Secret123","firstName":"Flow","city":"Pune"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn();
        verify(userServiceClient).createProfile(any());
        long userId = json(registered).path("user").path("id").asLong();

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"FLOW@example.com","password":"Secret123"}"""))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = json(login).path("accessToken").asText();

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value("flow@example.com"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        String body = """
                {"email":"dupe@example.com","password":"Secret123","businessName":"Dupe Hall","contactName":"D","phone":"+919999999999"}""";
        mvc.perform(post("/api/auth/register/vendor").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("VENDOR"));
        verify(studyHallServiceClient).createVendorProfile(any());

        mvc.perform(post("/api/auth/register/vendor").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register/user").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","firstName":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void wrongPasswordReturns401WithGenericMessage() throws Exception {
        register("wrongpw@example.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrongpw@example.com","password":"Nope12345"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointRejectsMissingTamperedExpiredAndForeignTokens() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        String valid = TestTokens.token(1, "USER");
        String tampered = valid.substring(0, valid.length() - 4) + "AAAA";
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());

        String expired = TestTokens.token(1, "USER", TestTokens.SECRET, TestTokens.ISSUER, Duration.ofMinutes(-5));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());

        String otherKey = TestTokens.token(1, "USER", "a-completely-different-secret-key-0123456789", TestTokens.ISSUER,
                Duration.ofMinutes(5));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherKey))
                .andExpect(status().isUnauthorized());

        String wrongIssuer = TestTokens.token(1, "USER", TestTokens.SECRET, "someone-else", Duration.ofMinutes(5));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsSingleUseAndReuseRevokesTheFamily() throws Exception {
        JsonNode auth = register("refresh@example.com");
        String first = auth.path("refreshToken").asText();

        MvcResult rotated = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String second = json(rotated).path("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);

        // Replaying the old token is treated as theft ...
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first + "\"}"))
                .andExpect(status().isUnauthorized());
        // ... and the newer token of the same family is revoked as well.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refresh = register("logout@example.com").path("refreshToken").asText();
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpointsRequireTheInternalApiKeyNotAJwt() throws Exception {
        mvc.perform(get("/internal/accounts/stats"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/accounts/stats").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(1, "ADMIN")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/accounts/stats").header("X-Internal-Api-Key", "wrong-key-wrong-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/accounts/stats").header("X-Internal-Api-Key", TestTokens.INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber());
    }

    @Test
    void suspendedAccountCannotLogInOrRefresh() throws Exception {
        JsonNode auth = register("suspend@example.com");
        long id = auth.path("user").path("id").asLong();

        mvc.perform(patch("/internal/accounts/{id}/status", id).header("X-Internal-Api-Key", TestTokens.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"suspend@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_SUSPENDED"));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + auth.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register/user").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Secret123\",\"firstName\":\"T\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
