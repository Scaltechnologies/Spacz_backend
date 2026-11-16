package com.spacz.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spacz.notification.entity.Notification;
import com.spacz.notification.entity.PushStatus;
import com.spacz.notification.push.PushMessage;
import com.spacz.notification.push.PushResult;
import com.spacz.notification.push.PushSender;
import com.spacz.notification.repository.DeviceTokenRepository;
import com.spacz.notification.repository.NotificationRepository;
import com.spacz.notification.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inbox, broadcasts, read state, devices, preferences, admin sending and push dispatch, through the
 * real security configuration (H2). The FCM sender is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiIntegrationTest {

    private static final AtomicLong IDS = new AtomicLong(1000);
    private static final String KEY = "X-Internal-Api-Key";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @MockitoBean
    private PushSender pushSender;

    @BeforeEach
    void pushDisabledByDefault() {
        reset(pushSender);
        when(pushSender.provider()).thenReturn("DISABLED");
        when(pushSender.enabled()).thenReturn(false);
    }

    // ---- internal API ----

    @Test
    void internalCreateNeedsTheApiKeyAndIsIdempotentByDedupKey() throws Exception {
        long userId = IDS.incrementAndGet();
        String body = personal(userId, "USER", "BOOKING", "BOOKING_CONFIRMED", "Booking confirmed", "dedup-" + userId);
        mvc.perform(post("/internal/notifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        long id = idOf(internal(body).andExpect(status().isCreated()));
        internal(body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertThat(notificationRepository.findById(id)).get()
                .extracting(Notification::getSourceService).isEqualTo("studyhall-service");
    }

    @Test
    void internalCreateValidatesInput() throws Exception {
        internal("{\"audience\":\"USER\",\"category\":\"BOOKING\",\"type\":\"bad type\",\"title\":\"t\",\"body\":\"b\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        internal("{\"recipientUserId\":5,\"audience\":\"ALL\",\"category\":\"BOOKING\",\"type\":\"X\",\"title\":\"t\",\"body\":\"b\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("INVALID_AUDIENCE"));
    }

    // ---- inbox ----

    @Test
    void inboxShowsOwnPersonalNotificationsAndBroadcastsForTheRole() throws Exception {
        long student = IDS.incrementAndGet();
        long otherStudent = IDS.incrementAndGet();
        String mine = "Mine " + student;
        String others = "Other " + otherStudent;
        String studentsOnly = "Students " + student;
        String everyone = "Everyone " + student;
        String vendorsOnly = "Vendors " + student;
        internal(personal(student, "USER", "BOOKING", "BOOKING_CONFIRMED", mine, null)).andExpect(status().isCreated());
        internal(personal(otherStudent, "USER", "BOOKING", "BOOKING_CONFIRMED", others, null)).andExpect(status().isCreated());
        internal(broadcast("USER", "ANNOUNCEMENT", studentsOnly)).andExpect(status().isCreated());
        internal(broadcast("ALL", "ANNOUNCEMENT", everyone)).andExpect(status().isCreated());
        internal(broadcast("VENDOR", "ANNOUNCEMENT", vendorsOnly)).andExpect(status().isCreated());

        mvc.perform(get("/api/notifications/inbox?size=100").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(student, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", hasItem(mine)))
                .andExpect(jsonPath("$.content[*].title", hasItem(studentsOnly)))
                .andExpect(jsonPath("$.content[*].title", hasItem(everyone)))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(others))))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(vendorsOnly))))
                .andExpect(jsonPath("$.content[0].title").value(everyone));

        long vendor = IDS.incrementAndGet();
        mvc.perform(get("/api/notifications/inbox?size=100").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(vendor, "VENDOR")))
                .andExpect(jsonPath("$.content[*].title", hasItem(vendorsOnly)))
                .andExpect(jsonPath("$.content[*].title", hasItem(everyone)))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(studentsOnly))))
                .andExpect(jsonPath("$.content[*].title", not(hasItem(mine))));

        mvc.perform(get("/api/notifications/inbox?category=BOOKING").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(student, "USER")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value(mine))
                .andExpect(jsonPath("$.content[0].broadcast").value(false));

        mvc.perform(get("/api/notifications/inbox")).andExpect(status().isUnauthorized());
    }

    @Test
    void readStateIsPerAccountForBroadcasts() throws Exception {
        long a = IDS.incrementAndGet();
        long b = IDS.incrementAndGet();
        String tokenA = TestTokens.bearer(a, "USER");
        String tokenB = TestTokens.bearer(b, "USER");
        markAllRead(tokenA);
        markAllRead(tokenB);

        long personal = idOf(internal(personal(a, "USER", "BOOKING", "BOOKING_EXPIRED", "Hold expired", null)));
        long broadcast = idOf(internal(broadcast("USER", "OFFER", "Offer for " + a)));
        assertUnread(tokenA, 2);
        assertUnread(tokenB, 1);

        mvc.perform(post("/api/notifications/inbox/" + broadcast + "/read").header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(post("/api/notifications/inbox/" + broadcast + "/read").header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(status().isOk());
        assertUnread(tokenA, 1);
        assertUnread(tokenB, 1);

        mvc.perform(get("/api/notifications/inbox?unreadOnly=true").header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(personal));

        mvc.perform(post("/api/notifications/inbox/" + personal + "/read").header(HttpHeaders.AUTHORIZATION, tokenA))
                .andExpect(jsonPath("$.read").value(true));
        assertUnread(tokenA, 0);

        mvc.perform(post("/api/notifications/inbox/read-all").header(HttpHeaders.AUTHORIZATION, tokenB))
                .andExpect(jsonPath("$.updated").value(1));
        assertUnread(tokenB, 0);
    }

    @Test
    void notificationsOfOtherAccountsAreNotFound() throws Exception {
        long owner = IDS.incrementAndGet();
        long id = idOf(internal(personal(owner, "USER", "BOOKING", "BOOKING_CONFIRMED", "Private", null)));
        long vendorBroadcast = idOf(internal(broadcast("VENDOR", "ANNOUNCEMENT", "For vendors")));
        String stranger = TestTokens.bearer(IDS.incrementAndGet(), "USER");

        mvc.perform(get("/api/notifications/inbox/" + id).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/inbox/" + id + "/read").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/notifications/inbox/" + id).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications/inbox/" + vendorBroadcast).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications/inbox/" + id).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(owner, "USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Private"));
    }

    @Test
    void dismissDeletesPersonalAndHidesBroadcastOnlyForTheCaller() throws Exception {
        long a = IDS.incrementAndGet();
        long b = IDS.incrementAndGet();
        long personal = idOf(internal(personal(a, "USER", "UPLOAD", "DOCUMENT_UPLOADED", "Uploaded", null)));
        long broadcast = idOf(internal(broadcast("USER", "ANNOUNCEMENT", "Dismiss me " + a)));

        mvc.perform(delete("/api/notifications/inbox/" + personal).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(a, "USER")))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/notifications/inbox/" + broadcast).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(a, "USER")))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.findById(personal)).isEmpty();
        mvc.perform(get("/api/notifications/inbox/" + broadcast).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(a, "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications/inbox/" + broadcast).header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(b, "USER")))
                .andExpect(status().isOk());
    }

    // ---- devices & preferences ----

    @Test
    void devicesMustMatchTheAppOfTheRoleAndMoveBetweenAccounts() throws Exception {
        long student = IDS.incrementAndGet();
        long otherStudent = IDS.incrementAndGet();
        String token = "fcm-token-" + student;

        registerDevice(TestTokens.bearer(student, "USER"), token, "PARTNER_APP")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("APP_ROLE_MISMATCH"));
        registerDevice(TestTokens.bearer(student, "USER"), token, "USER_APP")
                .andExpect(status().isOk()).andExpect(jsonPath("$.app").value("USER_APP"));
        registerDevice(TestTokens.bearer(student, "USER"), token, "USER_APP").andExpect(status().isOk());
        assertThat(deviceTokenRepository.findByUserIdOrderByLastSeenAtDesc(student)).hasSize(1);

        registerDevice(TestTokens.bearer(otherStudent, "USER"), token, "USER_APP").andExpect(status().isOk());
        assertThat(deviceTokenRepository.findByUserIdOrderByLastSeenAtDesc(student)).isEmpty();
        assertThat(deviceTokenRepository.findByToken(token)).get().extracting(d -> d.getUserId()).isEqualTo(otherStudent);

        // the previous owner cannot unregister it any more; the current owner can
        unregister(TestTokens.bearer(student, "USER"), token);
        assertThat(deviceTokenRepository.findByToken(token)).isPresent();
        unregister(TestTokens.bearer(otherStudent, "USER"), token);
        assertThat(deviceTokenRepository.findByToken(token)).isEmpty();

        long vendor = IDS.incrementAndGet();
        registerDevice(TestTokens.bearer(vendor, "VENDOR"), "partner-" + vendor, "PARTNER_APP").andExpect(status().isOk());
        mvc.perform(get("/api/notifications/devices").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(vendor, "VENDOR")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].app").value("PARTNER_APP"));
    }

    @Test
    void preferencesDefaultToPushOnAndCanBeChangedPerCategory() throws Exception {
        String token = TestTokens.bearer(IDS.incrementAndGet(), "USER");
        mvc.perform(get("/api/notifications/preferences").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$[?(@.category == 'OFFER')].pushEnabled").value(true));
        mvc.perform(put("/api/notifications/preferences").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferences\":[{\"category\":\"OFFER\",\"pushEnabled\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.category == 'OFFER')].pushEnabled").value(false))
                .andExpect(jsonPath("$[?(@.category == 'BOOKING')].pushEnabled").value(true));
        mvc.perform(put("/api/notifications/preferences").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"preferences\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoriesArePublic() throws Exception {
        mvc.perform(get("/api/notifications/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.category == 'BOOKING')].androidChannelId").value("spacz_booking"));
    }

    // ---- admin ----

    @Test
    void onlyAdminsCanSendAndTheirBroadcastsReachTheAudience() throws Exception {
        long admin = IDS.incrementAndGet();
        long student = IDS.incrementAndGet();
        String body = "{\"audience\":\"USER\",\"title\":\"Exam week hours " + student + "\",\"body\":\"Halls open till 11pm\"}";

        mvc.perform(post("/api/notifications/admin/send").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(student, "USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/admin/send").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"body\":\"b\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/admin/send").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].category").value("ANNOUNCEMENT"))
                .andExpect(jsonPath("$[0].createdBy").value(admin))
                .andExpect(jsonPath("$[0].pushStatus").value("PENDING"));

        mvc.perform(get("/api/notifications/inbox?category=ANNOUNCEMENT&size=100")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(student, "USER")))
                .andExpect(jsonPath("$.content[*].title", hasItem("Exam week hours " + student)));

        long targeted = IDS.incrementAndGet();
        mvc.perform(post("/api/notifications/admin/send").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(admin, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[" + targeted + "," + targeted + "],\"title\":\"Hi\",\"body\":\"Just you\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].recipientUserId").value(targeted));

        mvc.perform(get("/api/notifications/admin/sent").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recipientUserId").value(targeted));
        mvc.perform(get("/api/notifications/admin/push-status").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("DISABLED"));
    }

    // ---- push ----

    @Test
    void pushIsMarkedDisabledWhenFcmIsNotConfigured() throws Exception {
        long id = idOf(internal(personal(IDS.incrementAndGet(), "USER", "BOOKING", "BOOKING_CONFIRMED", "t", null)));
        awaitPushStatus(id, PushStatus.DISABLED);
        verify(pushSender, never()).send(any(), anyList());
    }

    @Test
    void personalPushGoesToTheRecipientsDevicesAndDropsTokensFcmRejects() throws Exception {
        enablePush();
        long student = IDS.incrementAndGet();
        String bearer = TestTokens.bearer(student, "USER");
        registerDevice(bearer, "good-" + student, "USER_APP").andExpect(status().isOk());
        registerDevice(bearer, "gone-" + student, "USER_APP").andExpect(status().isOk());
        when(pushSender.send(any(), anyList())).thenReturn(new PushResult(1, 1, Set.of("gone-" + student)));

        long id = idOf(internal("{\"recipientUserId\":" + student + ",\"audience\":\"USER\",\"category\":\"BOOKING\","
                + "\"type\":\"BOOKING_CONFIRMED\",\"title\":\"Confirmed\",\"body\":\"Seat A1\",\"entityType\":\"BOOKING\","
                + "\"entityId\":42,\"data\":{\"screen\":\"booking\"},\"highPriority\":true}"));

        awaitPushStatus(id, PushStatus.SENT);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(message.capture(), tokens.capture());
        assertThat(tokens.getValue()).containsExactlyInAnyOrder("good-" + student, "gone-" + student);
        assertThat(message.getValue().data()).containsEntry("screen", "booking").containsEntry("entityId", "42")
                .containsEntry("notificationId", String.valueOf(id)).containsEntry("type", "BOOKING_CONFIRMED");
        assertThat(message.getValue().androidChannelId()).isEqualTo("spacz_booking");
        assertThat(message.getValue().highPriority()).isTrue();
        assertThat(deviceTokenRepository.findByToken("gone-" + student)).isEmpty();
        assertThat(deviceTokenRepository.findByToken("good-" + student)).isPresent();
    }

    @Test
    void pushRespectsMutedCategoriesAndMissingDevices() throws Exception {
        enablePush();
        long muted = IDS.incrementAndGet();
        registerDevice(TestTokens.bearer(muted, "USER"), "muted-" + muted, "USER_APP").andExpect(status().isOk());
        mvc.perform(put("/api/notifications/preferences").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(muted, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferences\":[{\"category\":\"OFFER\",\"pushEnabled\":false}]}")).andExpect(status().isOk());

        long mutedId = idOf(internal(personal(muted, "USER", "OFFER", "OFFER_LIVE", "Offer", null)));
        long noDeviceId = idOf(internal(personal(IDS.incrementAndGet(), "USER", "BOOKING", "BOOKING_CONFIRMED", "t", null)));
        long inAppOnly = idOf(internal("{\"recipientUserId\":" + muted + ",\"audience\":\"USER\",\"category\":\"ACTIVITY\","
                + "\"type\":\"PROFILE_UPDATED\",\"title\":\"Saved\",\"body\":\"Profile updated\",\"push\":false}"));

        awaitPushStatus(mutedId, PushStatus.MUTED);
        awaitPushStatus(noDeviceId, PushStatus.NO_DEVICES);
        assertThat(notificationRepository.findById(inAppOnly)).get()
                .extracting(Notification::getPushStatus).isEqualTo(PushStatus.NOT_REQUESTED);
        verify(pushSender, never()).send(any(), anyList());
    }

    @Test
    void broadcastPushReachesEveryDeviceOfTheAudienceExceptMutedOnes() throws Exception {
        enablePush();
        long vendor = IDS.incrementAndGet();
        long mutedVendor = IDS.incrementAndGet();
        long student = IDS.incrementAndGet();
        registerDevice(TestTokens.bearer(vendor, "VENDOR"), "v-" + vendor, "PARTNER_APP").andExpect(status().isOk());
        registerDevice(TestTokens.bearer(mutedVendor, "VENDOR"), "v-" + mutedVendor, "PARTNER_APP").andExpect(status().isOk());
        registerDevice(TestTokens.bearer(student, "USER"), "s-" + student, "USER_APP").andExpect(status().isOk());
        mvc.perform(put("/api/notifications/preferences").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(mutedVendor, "VENDOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preferences\":[{\"category\":\"SYSTEM\",\"pushEnabled\":false}]}")).andExpect(status().isOk());
        when(pushSender.send(any(), anyList())).thenAnswer(inv -> new PushResult(((List<?>) inv.getArgument(1)).size(), 0, Set.of()));

        long id = idOf(internal("{\"audience\":\"VENDOR\",\"category\":\"SYSTEM\",\"type\":\"MAINTENANCE\","
                + "\"title\":\"Maintenance\",\"body\":\"Tonight 1-2am\"}"));

        awaitPushStatus(id, PushStatus.SENT);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> tokens = ArgumentCaptor.forClass(List.class);
        verify(pushSender, timeout(2000).atLeastOnce()).send(any(), tokens.capture());
        List<String> sentTo = tokens.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(sentTo).contains("v-" + vendor).doesNotContain("v-" + mutedVendor, "s-" + student);
    }

    // ---- helpers ----

    private void enablePush() {
        when(pushSender.enabled()).thenReturn(true);
        when(pushSender.provider()).thenReturn("FCM");
    }

    private ResultActions internal(String body) throws Exception {
        return mvc.perform(post("/internal/notifications").header(KEY, TestTokens.INTERNAL_KEY)
                .header("X-Source-Service", "studyhall-service")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions registerDevice(String bearer, String token, String app) throws Exception {
        return mvc.perform(post("/api/notifications/devices").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"app\":\"" + app + "\",\"platform\":\"ANDROID\",\"appVersion\":\"1.4.0\"}"));
    }

    private void unregister(String bearer, String token) throws Exception {
        mvc.perform(post("/api/notifications/devices/unregister").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }

    private void markAllRead(String bearer) throws Exception {
        mvc.perform(post("/api/notifications/inbox/read-all").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private void assertUnread(String bearer, long expected) throws Exception {
        mvc.perform(get("/api/notifications/inbox/unread-count").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unread").value(expected));
    }

    private long idOf(ResultActions result) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        assertThat(json.has("id")).as("response has an id: %s", json).isTrue();
        return json.get("id").asLong();
    }

    private void awaitPushStatus(long id, PushStatus expected) throws InterruptedException {
        PushStatus actual = null;
        for (int i = 0; i < 50; i++) {
            actual = notificationRepository.findById(id).map(Notification::getPushStatus).orElse(null);
            if (actual == expected) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(actual).as("push status of notification %d", id).isEqualTo(expected);
    }

    private static String personal(long userId, String role, String category, String type, String title, String dedupKey) {
        return "{\"recipientUserId\":" + userId + ",\"audience\":\"" + role + "\",\"category\":\"" + category
                + "\",\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"body\":\"Body of " + title + "\""
                + (dedupKey != null ? ",\"dedupKey\":\"" + dedupKey + "\"" : "") + "}";
    }

    private static String broadcast(String audience, String category, String title) {
        return "{\"audience\":\"" + audience + "\",\"category\":\"" + category + "\",\"type\":\"ANNOUNCEMENT\","
                + "\"title\":\"" + title + "\",\"body\":\"Body of " + title + "\",\"push\":false}";
    }
}
