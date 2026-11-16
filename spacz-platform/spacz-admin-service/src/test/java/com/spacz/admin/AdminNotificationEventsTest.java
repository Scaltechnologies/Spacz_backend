package com.spacz.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.UserServiceClient;
import com.spacz.admin.notify.NotificationEvent;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications caused by admin actions: account status, live offers, published updates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class AdminNotificationEventsTest {

    private static final String ADMIN = TestTokens.bearer(1, "ADMIN");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private AuthServiceClient authClient;
    @MockitoBean
    private UserServiceClient userClient;
    @MockitoBean
    private StudyHallServiceClient studyHallClient;

    @Test
    void suspendingAndActivatingAnAccountNotifiesItsOwner() throws Exception {
        when(authClient.updateStatus(4401L, "SUSPENDED")).thenReturn(AdminFixtures.account(4401L, "VENDOR", "SUSPENDED"));
        when(authClient.updateStatus(4401L, "ACTIVE")).thenReturn(AdminFixtures.account(4401L, "VENDOR", "ACTIVE"));

        admin(post("/api/admin/users/4401/suspend"), "{\"reason\":\"Fake listings\"}");
        admin(post("/api/admin/users/4401/activate"), "{}");

        NotificationEvent suspended = only("ACCOUNT_SUSPENDED");
        assertThat(suspended.recipientUserId()).isEqualTo(4401L);
        assertThat(suspended.audience()).isEqualTo("VENDOR");
        assertThat(suspended.body()).contains("Reason: Fake listings");
        assertThat(only("ACCOUNT_ACTIVATED").recipientUserId()).isEqualTo(4401L);
    }

    @Test
    void liveOffersAreAnnouncedToStudentsOncePerActivation() throws Exception {
        Instant now = Instant.now();
        String offer = """
                {"title":"%s","description":"Flat 100 off","discountType":"FLAT","discountValue":100,
                 "startAt":"%s","endAt":"%s","active":%s}""";
        admin(post("/api/admin/offers"), offer.formatted("Live now", now.minus(1, ChronoUnit.HOURS),
                now.plus(7, ChronoUnit.DAYS), true));
        long scheduled = id(admin(post("/api/admin/offers"), offer.formatted("Next month", now.plus(30, ChronoUnit.DAYS),
                now.plus(60, ChronoUnit.DAYS), true)));
        long disabled = id(admin(post("/api/admin/offers"), offer.formatted("Disabled", now.minus(1, ChronoUnit.HOURS),
                now.plus(7, ChronoUnit.DAYS), false)));
        assertThat(byType("OFFER_LIVE")).extracting(NotificationEvent::title).containsExactly("New offer: Live now");

        admin(patch("/api/admin/offers/" + disabled + "/status"), "{\"active\":true}");
        admin(patch("/api/admin/offers/" + disabled + "/status"), "{\"active\":true}");
        admin(patch("/api/admin/offers/" + scheduled + "/status"), "{\"active\":true}");
        assertThat(byType("OFFER_LIVE")).extracting(NotificationEvent::title)
                .containsExactly("New offer: Live now", "New offer: Disabled");
        assertThat(byType("OFFER_LIVE")).allSatisfy(n -> {
            assertThat(n.recipientUserId()).isNull();
            assertThat(n.audience()).isEqualTo("USER");
            assertThat(n.push()).isTrue();
        });
    }

    @Test
    void publishingAnUpdateBroadcastsItToStudents() throws Exception {
        long draft = id(admin(post("/api/admin/updates"), "{\"title\":\"Draft\",\"description\":\"Later\"}"));
        assertThat(byType("PLATFORM_UPDATE")).isEmpty();

        admin(patch("/api/admin/updates/" + draft + "/status"), "{\"active\":true}");
        admin(patch("/api/admin/updates/" + draft + "/status"), "{\"active\":true}");
        admin(post("/api/admin/updates"), "{\"title\":\"New halls\",\"description\":\"Three new halls\",\"published\":true}");

        assertThat(byType("PLATFORM_UPDATE")).extracting(NotificationEvent::title).containsExactly("Draft", "New halls");
        assertThat(byType("PLATFORM_UPDATE")).allSatisfy(n -> {
            assertThat(n.audience()).isEqualTo("USER");
            assertThat(n.category()).isEqualTo("ANNOUNCEMENT");
        });
    }

    private org.springframework.test.web.servlet.ResultActions admin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful());
    }

    private long id(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    private List<NotificationEvent> byType(String type) {
        return events.stream(NotificationEvent.class).filter(n -> n.type().equals(type)).toList();
    }

    private NotificationEvent only(String type) {
        List<NotificationEvent> matches = byType(type);
        assertThat(matches).as("notifications of type %s", type).hasSize(1);
        return matches.get(0);
    }
}
