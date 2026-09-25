package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.client.dto.UserSummaryDto;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vendor onboarding → hall → blocks/seats → approval → public search → booking → enrollment,
 * through the HTTP API with the real security configuration (H2; user-service mocked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyHallFlowIntegrationTest extends ApiTestSupport {

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void vendorToBookingEndToEnd() throws Exception {
        long vendorId = 500;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        registeredVendor(vendorId);

        // 1. Draft vendor cannot submit an incomplete profile.
        send(post("/api/vendors/me/submit"), vendor, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PROFILE_INCOMPLETE"));
        send(put("/api/vendors/me"), vendor, """
                {"businessName":"Focus Study Hall","contactName":"Ravi","phone":"+919812345678",
                 "addressLine":"Plot 12, Ameerpet","city":"Hyderabad"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingForSubmission.length()").value(0));

        // 2. Hall with a default price; a premium AC block with its own daily + monthly price.
        JsonNode hall = json(send(post("/api/studyhalls"), vendor, """
                {"name":"Focus Hall Ameerpet","addressLine":"Plot 12","city":"Hyderabad","state":"Telangana",
                 "latitude":17.4375,"longitude":78.4482,"pricePerDay":120.00,"openingTime":"06:00","closingTime":"22:00"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.operatingHours.length()").value(7)));
        long hallId = hall.path("id").asLong();

        JsonNode block = json(send(post("/api/studyhalls/{id}/blocks", hallId), vendor, """
                {"name":"AC Hall","totalRows":3,"totalColumns":4,"gaps":[{"row":1,"column":2},{"row":2,"column":2}],
                 "dailyPrice":150.00,"monthlyPrice":2500.00,"amenityIds":[2]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(10))
                .andExpect(jsonPath("$.seats[0].seatNumber").value("A1"))
                .andExpect(jsonPath("$.seats[0].pricePerDay").value(150.00))
                .andExpect(jsonPath("$.amenities[0].code").value("AC")));
        long seatA1 = block.path("seats").get(0).path("id").asLong();
        long seatA3 = block.path("seats").get(1).path("id").asLong();
        long seatA4 = block.path("seats").get(2).path("id").asLong();

        // 3. Ownership and roles.
        approvedVendor(501);
        send(put("/api/studyhalls/{id}", hallId), TestTokens.bearer(501, "VENDOR"), """
                {"name":"x","addressLine":"x","city":"x","state":"x","pricePerDay":1}""")
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/studyhalls/{id}", hallId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/studyhalls/{id}", hallId).header(HttpHeaders.AUTHORIZATION, vendor))
                .andExpect(status().isOk());
        send(post("/api/studyhalls"), TestTokens.bearer(900, "USER"), "{}").andExpect(status().isForbidden());

        // 4. Supported exams come from the local catalog.
        send(post("/api/studyhalls/{id}/programs", hallId), vendor, "{\"programIds\":[1, 9]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programs.length()").value(2));
        send(post("/api/studyhalls/{id}/programs", hallId), vendor, "{\"programIds\":[99999]}")
                .andExpect(status().isNotFound());

        // 5. Vendor + hall submitted; approving both takes the hall live.
        send(post("/api/vendors/me/submit"), vendor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        send(post("/api/studyhalls/{id}/submit", hallId), vendor, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        internal(patch("/internal/studyhalls/{id}/status", hallId), "{\"action\":\"APPROVE\"}")
                .andExpect(jsonPath("$.status").value("APPROVED"));
        internal(patch("/internal/vendors/{id}/status", vendorId), "{\"action\":\"APPROVE\"}")
                .andExpect(jsonPath("$.profile.status").value("APPROVED"));
        mvc.perform(get("/api/studyhalls/{id}", hallId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        // 6. Public search.
        mvc.perform(get("/api/studyhalls").param("city", "hyderabad").param("programId", "1").param("amenityIds", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].seatCount").value(10));
        mvc.perform(get("/api/vendors/{id}", vendorId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.liveStudyHalls").value(1));

        // 7. Daily booking, double-booking prevention, monthly booking, confirmation → enrollment.
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5);
        String daily = "{\"studyHallId\":" + hallId + ",\"seatId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}";
        long bookingId = id(send(post("/api/bookings"), TestTokens.bearer(900, "USER"),
                daily.formatted(seatA1, start, start.plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("DAILY"))
                .andExpect(jsonPath("$.totalPrice").value(450.00)), "id");
        send(post("/api/bookings"), TestTokens.bearer(901, "USER"), daily.formatted(seatA1, start.plusDays(1), start.plusDays(4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SEAT_UNAVAILABLE"));
        send(post("/api/bookings"), TestTokens.bearer(901, "USER"), """
                {"studyHallId":%d,"seatId":%d,"plan":"MONTHLY","startDate":"%s","months":2,"programId":9}"""
                .formatted(hallId, seatA3, start))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.units").value(2))
                .andExpect(jsonPath("$.totalPrice").value(5000.00))
                .andExpect(jsonPath("$.programName").value("GATE"));
        send(post("/api/bookings/{id}/confirm", bookingId), TestTokens.bearer(900, "USER"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 8. Walk-in student holds a seat; it is no longer bookable online.
        send(post("/api/studyhalls/{id}/enrollments", hallId), vendor, """
                {"guestName":"Kiran Rao","guestPhone":"+919876500000","seatId":%d,"programId":1,"startDate":"%s"}"""
                .formatted(seatA4, start))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.student.name").value("Kiran Rao"))
                .andExpect(jsonPath("$.student.account").value(false))
                .andExpect(jsonPath("$.source").value("WALK_IN"));
        send(post("/api/bookings"), TestTokens.bearer(902, "USER"), daily.formatted(seatA4, start, start))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("SEAT_NOT_BOOKABLE"));

        // 9. The vendor's students: the confirmed booker and the walk-in.
        when(userServiceClient.findUsers(any())).thenReturn(List.of(
                new UserSummaryDto(900L, "Asha", "Rao", "asha@example.com", "+919800000000", "Hyderabad")));
        mvc.perform(get("/api/vendors/me/students").header(HttpHeaders.AUTHORIZATION, vendor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/vendors/me/students").param("search", "kiran").header(HttpHeaders.AUTHORIZATION, vendor))
                .andExpect(jsonPath("$.totalElements").value(1));
        internal(get("/internal/enrollments").param("userId", "900"), null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studyHallName").value("Focus Hall Ameerpet"));

        // 10. Suspension hides the hall and blocks edits.
        internal(patch("/internal/vendors/{id}/status", vendorId), "{\"action\":\"SUSPEND\",\"reason\":\"Complaints\"}")
                .andExpect(status().isOk());
        mvc.perform(get("/api/studyhalls/{id}", hallId)).andExpect(status().isNotFound());
        send(post("/api/studyhalls"), vendor, """
                {"name":"Another","addressLine":"x","city":"Pune","state":"MH","pricePerDay":100,"openingTime":"06:00","closingTime":"22:00"}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("VENDOR_SUSPENDED"));
    }

    @Test
    void programAndAmenityCatalogsArePublicToReadAndAdminOnlyToWrite() throws Exception {
        mvc.perform(get("/api/programs").param("search", "eamcet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("EAMCET"));
        mvc.perform(get("/api/amenities")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(15));

        String program = "{\"code\":\"BANK_X\",\"name\":\"Bank X\",\"category\":\"Banking\"}";
        send(post("/api/programs"), null, program).andExpect(status().isUnauthorized());
        send(post("/api/programs"), TestTokens.bearer(5, "VENDOR"), program).andExpect(status().isForbidden());
        long id = id(send(post("/api/programs"), TestTokens.bearer(1, "ADMIN"), program)
                .andExpect(status().isCreated()), "id");
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/programs/{id}", id),
                TestTokens.bearer(1, "ADMIN"), null).andExpect(status().isNoContent());
        mvc.perform(get("/api/programs/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void statsAreServedOnlyToInternalCallers() throws Exception {
        mvc.perform(get("/internal/stats").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(1, "ADMIN")))
                .andExpect(status().isUnauthorized());
        internal(get("/internal/stats"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePrograms").isNumber());
    }

    @Test
    void searchRejectsUnknownSortFields() throws Exception {
        mvc.perform(get("/api/studyhalls").param("sort", "vendor.email,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SORT"));
    }
}
