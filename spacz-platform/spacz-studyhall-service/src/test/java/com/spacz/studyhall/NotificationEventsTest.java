package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.notify.NotificationEvent;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which notifications the vendor → approval → booking flow sends, to whom, and whether they push.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class NotificationEventsTest extends ApiTestSupport {

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Autowired
    private ApplicationEvents events;

    @Test
    void approvalAndBookingFlowNotifiesVendorsStudentsAndAdmins() throws Exception {
        long vendorId = 7701;
        long studentId = 7790;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        registeredVendor(vendorId);
        send(put("/api/vendors/me"), vendor, """
                {"businessName":"Quiet Corner","contactName":"Meena","phone":"+919812345000",
                 "addressLine":"Road 2","city":"Hyderabad"}""").andExpect(status().isOk());
        long hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Quiet Corner Kondapur","addressLine":"Road 2","city":"Hyderabad","state":"Telangana",
                 "pricePerDay":100.00,"openingTime":"06:00","closingTime":"22:00"}""").andExpect(status().isCreated()), "id");
        JsonNode block = json(send(post("/api/studyhalls/{id}/blocks", hallId), vendor,
                "{\"name\":\"Main\",\"totalRows\":1,\"totalColumns\":2,\"dailyPrice\":100.00}").andExpect(status().isCreated()));
        long seatId = block.path("seats").get(0).path("id").asLong();
        assertThat(only("STUDY_HALL_CREATED")).satisfies(n -> {
            assertThat(n.recipientUserId()).isEqualTo(vendorId);
            assertThat(n.push()).isFalse();
        });

        // Submissions ask the admins (broadcast to ADMIN) and acknowledge the vendor in-app.
        send(post("/api/vendors/me/submit"), vendor, null).andExpect(status().isOk());
        send(post("/api/studyhalls/{id}/submit", hallId), vendor, null).andExpect(status().isOk());
        assertThat(only("VENDOR_APPROVAL_REQUESTED")).satisfies(n -> {
            assertThat(n.recipientUserId()).isNull();
            assertThat(n.audience()).isEqualTo("ADMIN");
            assertThat(n.category()).isEqualTo("REQUEST");
            assertThat(n.body()).contains("Quiet Corner");
        });
        assertThat(only("STUDY_HALL_APPROVAL_REQUESTED").body()).contains("Quiet Corner Kondapur");
        assertThat(only("VENDOR_SUBMITTED").recipientUserId()).isEqualTo(vendorId);

        // Approvals (applied through the internal API by admin-service) reach the vendor with push.
        internal(patch("/internal/studyhalls/{id}/status", hallId), "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
        assertThat(only("STUDY_HALL_APPROVED")).satisfies(n -> {
            assertThat(n.push()).isTrue();
            assertThat(n.body()).contains("as soon as your partner account is approved");
        });
        internal(patch("/internal/vendors/{id}/status", vendorId), "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
        assertThat(only("VENDOR_APPROVED")).satisfies(n -> {
            assertThat(n.recipientUserId()).isEqualTo(vendorId);
            assertThat(n.category()).isEqualTo("APPROVAL");
        });

        // Booking: hold → student; confirm → student + vendor; cancel → student (in-app) + vendor.
        String student = TestTokens.bearer(studentId, "USER");
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(3);
        long bookingId = id(send(post("/api/bookings"), student,
                "{\"studyHallId\":" + hallId + ",\"seatId\":" + seatId + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + start + "\"}")
                .andExpect(status().isCreated()), "id");
        assertThat(only("BOOKING_HELD")).satisfies(n -> {
            assertThat(n.recipientUserId()).isEqualTo(studentId);
            assertThat(n.entityId()).isEqualTo(bookingId);
            assertThat(n.data()).containsEntry("bookingId", String.valueOf(bookingId));
        });

        send(post("/api/bookings/{id}/confirm", bookingId), student, null).andExpect(status().isOk());
        assertThat(only("BOOKING_CONFIRMED").recipientUserId()).isEqualTo(studentId);
        assertThat(only("BOOKING_RECEIVED")).satisfies(n -> {
            assertThat(n.recipientUserId()).isEqualTo(vendorId);
            assertThat(n.audience()).isEqualTo("VENDOR");
            assertThat(n.title()).contains("Quiet Corner Kondapur");
        });

        send(post("/api/bookings/{id}/cancel", bookingId), student, "{\"reason\":\"Plans changed\"}").andExpect(status().isOk());
        List<NotificationEvent> cancelled = byType("BOOKING_CANCELLED");
        assertThat(cancelled).extracting(NotificationEvent::recipientUserId).containsExactlyInAnyOrder(studentId, vendorId);
        assertThat(cancelled).filteredOn(n -> n.recipientUserId() == studentId).allMatch(n -> !n.push());

        // Suspension carries the admin's reason.
        internal(patch("/internal/studyhalls/{id}/status", hallId), "{\"action\":\"SUSPEND\",\"reason\":\"Fire safety check\"}")
                .andExpect(status().isOk());
        assertThat(only("STUDY_HALL_SUSPENDED").body()).contains("Reason: Fire safety check");

        assertThat(events.stream(NotificationEvent.class)).allSatisfy(n -> assertThat(n.dedupKey()).isNotBlank());
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
