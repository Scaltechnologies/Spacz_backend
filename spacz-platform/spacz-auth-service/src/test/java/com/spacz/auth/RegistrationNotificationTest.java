package com.spacz.auth;

import com.spacz.auth.client.StudyHallServiceClient;
import com.spacz.auth.client.UserServiceClient;
import com.spacz.auth.notify.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-ups welcome the new account and tell the admins.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class RegistrationNotificationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private UserServiceClient userServiceClient;
    @MockitoBean
    private StudyHallServiceClient studyHallServiceClient;

    @Test
    void studentAndVendorRegistrationsNotifyTheNewAccountAndAdmins() throws Exception {
        mvc.perform(post("/api/auth/register/user").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"notify-student@example.com","password":"Secret123","firstName":"Asha"}"""))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register/vendor").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"notify-vendor@example.com","password":"Secret123","businessName":"Bright Desk",
                 "contactName":"Ravi","phone":"+919811100000"}"""))
                .andExpect(status().isCreated());

        List<NotificationEvent> sent = events.stream(NotificationEvent.class).toList();
        assertThat(sent).extracting(NotificationEvent::type)
                .containsExactly("WELCOME", "USER_REGISTERED", "WELCOME_PARTNER", "VENDOR_REGISTERED");
        assertThat(sent.get(0).recipientUserId()).isNotNull();
        assertThat(sent.get(0).audience()).isEqualTo("USER");
        assertThat(sent.get(2).audience()).isEqualTo("VENDOR");
        assertThat(sent.get(3)).satisfies(n -> {
            assertThat(n.recipientUserId()).isNull();
            assertThat(n.audience()).isEqualTo("ADMIN");
            assertThat(n.body()).contains("Bright Desk");
            assertThat(n.push()).isTrue();
        });
    }
}
