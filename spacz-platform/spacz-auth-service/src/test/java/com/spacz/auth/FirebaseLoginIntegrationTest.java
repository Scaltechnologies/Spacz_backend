package com.spacz.auth;

import com.spacz.auth.client.AdminServiceClient;
import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.client.dto.CreateVendorProfileRequest;
import com.spacz.auth.service.firebase.FirebaseTokenVerifier;
import com.spacz.auth.service.impl.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Firebase Phone Auth login and registration. Token verification itself is covered by
 * GoogleFirebaseTokenVerifierTest; here the verifier is stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FirebaseLoginIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private StudyHallServiceClient studyHallServiceClient;
    @MockitoBean
    private AdminServiceClient adminServiceClient;

    @Test
    void newVendorRegistersWithFirebaseAndLogsInAgainWithTheSameToken() throws Exception {
        when(firebaseTokenVerifier.verifyPhone("vendor-token")).thenReturn("+919733000001");

        // A first-time number without a role: register prompt, nothing created.
        firebase("vendor-token", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("REGISTRATION_REQUIRED"));

        firebase("vendor-token", "\"role\":\"VENDOR\",\"businessName\":\"Focus Hall\",\"contactName\":\"Ravi\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("VENDOR"))
                .andExpect(jsonPath("$.user.phone").value("+919733000001"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        ArgumentCaptor<CreateVendorProfileRequest> vendor = ArgumentCaptor.forClass(CreateVendorProfileRequest.class);
        verify(studyHallServiceClient).createVendorProfile(vendor.capture());
        assertThat(vendor.getValue().businessName()).isEqualTo("Focus Hall");
        assertThat(vendor.getValue().phone()).isEqualTo("+919733000001");

        // Next login: the existing account is found, no second profile is created.
        firebase("vendor-token", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("VENDOR"));
        verify(studyHallServiceClient, times(1)).createVendorProfile(vendor.capture());
    }

    @Test
    void invalidFirebaseTokenIsRejected() throws Exception {
        when(firebaseTokenVerifier.verifyPhone("bad")).thenThrow(new InvalidCredentialsException("invalid"));
        firebase("bad", "\"role\":\"VENDOR\"").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/otp/firebase").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotRegisterWithFirebase() throws Exception {
        when(firebaseTokenVerifier.verifyPhone("admin-token")).thenReturn("+919733000002");
        firebase("admin-token", "\"role\":\"ADMIN\"").andExpect(status().isForbidden());
    }

    private ResultActions firebase(String token, String extra) throws Exception {
        String body = "{\"idToken\":\"" + token + "\"" + (extra == null ? "" : "," + extra) + "}";
        return mvc.perform(post("/api/auth/otp/firebase").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
