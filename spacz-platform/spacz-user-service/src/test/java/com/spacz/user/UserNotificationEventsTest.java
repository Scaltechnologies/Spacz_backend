package com.spacz.user;

import com.spacz.user.client.StudyHallServiceClient;
import com.spacz.user.notify.NotificationEvent;
import com.spacz.user.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Student actions in user-service: feedback (once, not for the duplicate) and profile updates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class UserNotificationEventsTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private StudyHallServiceClient studyHallClient;

    @Test
    void feedbackAndProfileUpdatesNotifyInApp() throws Exception {
        long userId = 6601;
        String bearer = TestTokens.bearer(userId, "USER");
        mvc.perform(post("/internal/users").header("X-Internal-Api-Key", TestTokens.INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + userId + ",\"firstName\":\"Asha\"}"))
                .andExpect(status().isCreated());

        String feedback = "{\"rating\":4,\"comment\":\"Great halls\"}";
        mvc.perform(post("/api/users/me/feedback").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(feedback)).andExpect(status().is2xxSuccessful());
        mvc.perform(post("/api/users/me/feedback").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(feedback)).andExpect(status().is2xxSuccessful());
        mvc.perform(put("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Asha\",\"city\":\"Pune\"}"))
                .andExpect(status().isOk());

        List<NotificationEvent> sent = events.stream(NotificationEvent.class).toList();
        assertThat(sent).extracting(NotificationEvent::type)
                .containsExactly("FEEDBACK_SUBMITTED", "FEEDBACK_RECEIVED", "PROFILE_UPDATED");
        assertThat(sent.get(1)).satisfies(n -> {
            assertThat(n.audience()).isEqualTo("ADMIN");
            assertThat(n.body()).contains("4/5", "Great halls");
        });
        assertThat(sent).allSatisfy(n -> assertThat(n.push()).isFalse());
    }
}
