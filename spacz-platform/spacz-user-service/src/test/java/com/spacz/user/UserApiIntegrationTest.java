package com.spacz.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.user.client.StudyHallServiceClient;
import com.spacz.user.client.dto.EnrollmentDto;
import com.spacz.user.client.dto.ProgramDto;
import com.spacz.user.exception.ServiceUnavailableException;
import com.spacz.user.support.TestTokens;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profile ownership, exam/program choices (validated against studyhall-service, mocked here),
 * enrollments and activity, through the real security configuration (H2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(100);
    private static final String KEY = "X-Internal-Api-Key";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StudyHallServiceClient studyHallClient;

    @BeforeEach
    void catalog() {
        when(studyHallClient.findPrograms(Set.of(1L))).thenReturn(List.of(
                new ProgramDto(1L, "UPSC_CSE", "UPSC Civil Services", null, "Civil Services", true)));
        when(studyHallClient.findPrograms(Set.of(9L))).thenReturn(List.of(
                new ProgramDto(9L, "GATE", "GATE", null, "Engineering", true)));
        when(studyHallClient.findPrograms(Set.of(77L))).thenReturn(List.of(
                new ProgramDto(77L, "OLD", "Old Exam", null, "Other", false)));
        when(studyHallClient.findPrograms(Set.of(404L))).thenReturn(List.of());
    }

    @Test
    void profileIsCreatedIdempotentlyByAuthServiceEvenWithoutEmail() throws Exception {
        long userId = IDS.incrementAndGet();
        String body = "{\"userId\":" + userId + ",\"phone\":\"+919876543210\",\"firstName\":\"Asha\"}";
        mvc.perform(post("/internal/users").header(KEY, TestTokens.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+919876543210"));
        mvc.perform(post("/internal/users").header(KEY, TestTokens.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/internal/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointsActOnTheCaller() throws Exception {
        long userId = createProfile();
        String auth = TestTokens.bearer(userId, "USER");
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
        mvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Asha\",\"lastName\":\"Reddy\",\"preferredStudySlot\":\"MORNING\",\"dailyStudyHoursGoal\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Reddy"));
        mvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"dailyStudyHoursGoal\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void othersProfilesAreProtectedButAdminsCanRead() throws Exception {
        long owner = createProfile();
        long other = createProfile();
        mvc.perform(get("/api/users/{id}", owner).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(other, "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/{id}", owner).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(other, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/{id}", owner).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(1, "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/{id}/programs", owner).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(1, "ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/{id}", owner).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(owner, "VENDOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void programChoicesAreValidatedAgainstTheCatalogAndSnapshotted() throws Exception {
        long userId = createProfile();
        String auth = TestTokens.bearer(userId, "USER");

        MvcResult added = mvc.perform(post("/api/users/me/programs").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":1,\"startDate\":\"2026-10-01\",\"targetDate\":\"2027-05-25\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programCode").value("UPSC_CSE"))
                .andExpect(jsonPath("$.programName").value("UPSC Civil Services"))
                .andReturn();
        long choiceId = objectMapper.readTree(added.getResponse().getContentAsString()).path("id").asLong();

        mvc.perform(post("/api/users/me/programs").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"programId\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/users/me/programs").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"programId\":404}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/users/me/programs").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"programId\":77}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/users/me/programs/{id}", choiceId).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"programId\":9,\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value("GATE"))
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(get("/api/users/me/programs").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/users/me/programs/{id}", choiceId).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/users/me/activity").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].type").value("PROGRAM_REMOVED"));
    }

    @Test
    void catalogOutageReturns503() throws Exception {
        long userId = createProfile();
        when(studyHallClient.findPrograms(Set.of(2L))).thenThrow(new ServiceUnavailableException("studyhall-service", "down"));
        mvc.perform(post("/api/users/me/programs").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(userId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"programId\":2}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void enrollmentsComeFromStudyHallService() throws Exception {
        long userId = createProfile();
        when(studyHallClient.enrollments(any())).thenReturn(List.of(new EnrollmentDto(5L, 2L, "Focus Hall", null, 11L,
                "A1", "MONTHLY", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), "ACTIVE", "BOOKING")));
        mvc.perform(get("/api/users/me/enrollments").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studyHallName").value("Focus Hall"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    private long createProfile() throws Exception {
        long userId = IDS.incrementAndGet();
        mvc.perform(post("/internal/users").header(KEY, TestTokens.INTERNAL_KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + userId + ",\"email\":\"u" + userId + "@example.com\",\"firstName\":\"Asha\"}"))
                .andExpect(status().isCreated());
        return userId;
    }
}
