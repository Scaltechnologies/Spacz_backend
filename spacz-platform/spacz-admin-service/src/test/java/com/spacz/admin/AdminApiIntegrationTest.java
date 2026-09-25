package com.spacz.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.UserServiceClient;
import com.spacz.admin.client.dto.AccountStatsDto;
import com.spacz.admin.client.dto.UserProfileDto;
import com.spacz.admin.exception.BusinessRuleException;
import com.spacz.admin.exception.ServiceUnavailableException;
import com.spacz.admin.support.AdminFixtures;
import com.spacz.admin.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin authorization, approval/suspension workflow, audit trail (admin actions and domain events),
 * admin profile and dashboard degradation (downstream services mocked, audit log in H2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiIntegrationTest {

    private static final String ADMIN = TestTokens.bearer(1, "ADMIN");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthServiceClient authClient;
    @MockitoBean
    private UserServiceClient userClient;
    @MockitoBean
    private StudyHallServiceClient studyHallClient;

    @Test
    void onlyAdminsCanUseTheAdminApi() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/vendors").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(6, "VENDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardDegradesWhenAServiceIsDown() throws Exception {
        when(authClient.stats()).thenReturn(new AccountStatsDto(120, 110, 10, 12, 11, 1));
        when(studyHallClient.stats()).thenThrow(new ServiceUnavailableException("studyhall-service", "timeout"));

        mvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.totalUsers").value(120))
                .andExpect(jsonPath("$.vendors").doesNotExist())
                .andExpect(jsonPath("$.unavailableServices[0]").value("studyhall-service"));
    }

    @Test
    void approvingAVendorIsAuditedWithActorAndCorrelationId() throws Exception {
        when(studyHallClient.changeVendorStatus(eq(300L), any())).thenReturn(AdminFixtures.vendor(300L, "APPROVED"));

        mvc.perform(post("/api/admin/vendors/300/approve").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .header("X-Correlation-Id", "test-corr-300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.status").value("APPROVED"));

        mvc.perform(get("/api/admin/audit-logs").param("action", "VENDOR_APPROVED").param("entityId", "300")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorUserId").value(1))
                .andExpect(jsonPath("$.content[0].sourceService").value("admin-service"))
                .andExpect(jsonPath("$.content[0].correlationId").value("test-corr-300"))
                .andExpect(jsonPath("$.content[0].ipAddress").isNotEmpty());
    }

    @Test
    void suspensionRequiresAReason() throws Exception {
        mvc.perform(post("/api/admin/vendors/301/suspend").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        verify(studyHallClient, never()).changeVendorStatus(eq(301L), any());

        when(studyHallClient.changeVendorStatus(eq(301L), any())).thenReturn(AdminFixtures.vendor(301L, "SUSPENDED"));
        mvc.perform(post("/api/admin/vendors/301/suspend").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fake listings\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit-logs").param("action", "VENDOR_SUSPENDED").param("search", "fake listings")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void invalidTransitionsFromTheOwningServiceArePassedThroughAndNotAudited() throws Exception {
        when(studyHallClient.changeStudyHallStatus(eq(55L), any()))
                .thenThrow(new BusinessRuleException("INVALID_STATUS_TRANSITION", "Cannot approve a study hall in status DRAFT"));

        mvc.perform(post("/api/admin/studyhalls/55/approve").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(get("/api/admin/audit-logs").param("entityType", "STUDY_HALL").param("entityId", "55")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void suspendingAUserAccountIsAuditedAndAdminsCannotSuspendThemselves() throws Exception {
        when(authClient.updateStatus(77L, "SUSPENDED")).thenReturn(AdminFixtures.account(77L, "USER", "SUSPENDED"));

        mvc.perform(post("/api/admin/users/77/suspend").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Abusive behaviour\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/admin/audit-logs").param("action", "USER_SUSPENDED").param("entityId", "77")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(post("/api/admin/users/1/suspend").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"oops\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void studentDetailComposesAllServicesAndDegradesPerPart() throws Exception {
        when(authClient.getAccount(88L)).thenReturn(AdminFixtures.account(88L, "USER", "ACTIVE"));
        when(userClient.getProfile(88L)).thenReturn(new UserProfileDto(1L, 88L, null, "Asha", "Rao", "+9199", null,
                null, "Hyderabad", null, null, null, null, null));
        when(userClient.programs(88L)).thenReturn(objectMapper.readTree("[{\"programName\":\"GATE\"}]"));
        when(studyHallClient.enrollments(88L)).thenThrow(new ServiceUnavailableException("studyhall-service", "down"));

        mvc.perform(get("/api/admin/users/88").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.firstName").value("Asha"))
                .andExpect(jsonPath("$.programs[0].programName").value("GATE"))
                .andExpect(jsonPath("$.enrollments").doesNotExist())
                .andExpect(jsonPath("$.warnings.length()").value(1));
    }

    @Test
    void domainEventsFromOtherServicesLandInTheAuditLog() throws Exception {
        mvc.perform(post("/internal/audit-events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorRole\":\"SYSTEM\",\"action\":\"VENDOR_REGISTERED\",\"entityType\":\"VENDOR\",\"entityId\":900}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/audit-events").header("X-Internal-Api-Key", TestTokens.INTERNAL_KEY)
                        .header("X-Source-Service", "auth-service").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorUserId\":900,\"actorRole\":\"VENDOR\",\"action\":\"VENDOR_REGISTERED\","
                                + "\"entityType\":\"VENDOR\",\"entityId\":900,\"description\":\"Vendor registered with phone OTP\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/audit-logs").param("actorRole", "VENDOR").param("action", "VENDOR_REGISTERED")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.content[0].sourceService").value("auth-service"))
                .andExpect(jsonPath("$.content[0].entityId").value(900));
    }

    @Test
    void adminProfileIsCreatedOnFirstAccessAndEditable() throws Exception {
        mvc.perform(get("/api/admin/me").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(42, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminUserId").value(42))
                .andExpect(jsonPath("$.email").value("user42@test.local"));
        mvc.perform(put("/api/admin/me").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(42, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Priya Sharma\",\"title\":\"Ops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Priya Sharma"));
    }

    @Test
    void downstreamOutageReturns503() throws Exception {
        when(studyHallClient.searchVendors(any(), any(), any(), any()))
                .thenThrow(new ServiceUnavailableException("studyhall-service", "connection refused"));
        mvc.perform(get("/api/admin/vendors").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }
}
