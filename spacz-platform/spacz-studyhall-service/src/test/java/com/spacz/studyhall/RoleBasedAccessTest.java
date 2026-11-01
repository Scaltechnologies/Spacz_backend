package com.spacz.studyhall;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for the public API: what anonymous callers, students (USER), vendors (VENDOR) and
 * admins (ADMIN) may call, and that vendors only reach their own halls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleBasedAccessTest extends ApiTestSupport {

    private static final String USER = TestTokens.bearer(9100, "USER");
    private static final String VENDOR = TestTokens.bearer(9200, "VENDOR");
    private static final String ADMIN = TestTokens.bearer(9300, "ADMIN");

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void anonymousCallersOnlyReachPublicReads() throws Exception {
        expect(HttpMethod.GET, "/api/studyhalls", null, 200);
        expect(HttpMethod.GET, "/api/programs", null, 200);
        expect(HttpMethod.GET, "/api/amenities", null, 200);

        expect(HttpMethod.GET, "/api/programs/all", null, 401);
        expect(HttpMethod.GET, "/api/vendors/me", null, 401);
        expect(HttpMethod.POST, "/api/studyhalls", null, 401);
        expect(HttpMethod.POST, "/api/bookings", null, 401);
        expect(HttpMethod.GET, "/api/bookings/me", null, 401);
        expect(HttpMethod.GET, "/api/studyhalls/1/enrollments", null, 401);
        expect(HttpMethod.GET, "/api/owners", null, 401);
    }

    @Test
    void studentsCannotUseVendorOrAdminEndpoints() throws Exception {
        expect(HttpMethod.POST, "/api/studyhalls", USER, 403);
        expect(HttpMethod.PUT, "/api/studyhalls/1", USER, 403);
        expect(HttpMethod.POST, "/api/studyhalls/1/blocks", USER, 403);
        expect(HttpMethod.GET, "/api/studyhalls/1/enrollments", USER, 403);
        expect(HttpMethod.GET, "/api/studyhalls/1/bookings", USER, 403);
        expect(HttpMethod.GET, "/api/vendors/me", USER, 403);
        expect(HttpMethod.POST, "/api/vendors", USER, 403);
        expect(HttpMethod.GET, "/api/owners", USER, 403);
        expect(HttpMethod.POST, "/api/programs", USER, 403);
        expect(HttpMethod.GET, "/api/programs/all", USER, 403);
        expect(HttpMethod.POST, "/api/amenities", USER, 403);

        expect(HttpMethod.GET, "/api/bookings/me", USER, 200);
    }

    @Test
    void vendorsCannotBookOrManageCatalogs() throws Exception {
        expect(HttpMethod.POST, "/api/bookings", VENDOR, 403);
        expect(HttpMethod.GET, "/api/bookings/me", VENDOR, 403);
        expect(HttpMethod.POST, "/api/bookings/1/confirm", VENDOR, 403);
        expect(HttpMethod.POST, "/api/bookings/1/cancel", VENDOR, 403);
        expect(HttpMethod.POST, "/api/programs", VENDOR, 403);
        expect(HttpMethod.DELETE, "/api/programs/1", VENDOR, 403);
        expect(HttpMethod.GET, "/api/amenities/all", VENDOR, 403);
        expect(HttpMethod.PUT, "/api/amenities/1", VENDOR, 403);
    }

    @Test
    void adminsManageCatalogsButNotVendorOrStudentActions() throws Exception {
        expect(HttpMethod.GET, "/api/programs/all", ADMIN, 200);
        expect(HttpMethod.GET, "/api/amenities/all", ADMIN, 200);

        // Admins moderate through admin-service; they cannot act as a vendor or a student here.
        expect(HttpMethod.POST, "/api/studyhalls", ADMIN, 403);
        expect(HttpMethod.GET, "/api/vendors/me", ADMIN, 403);
        expect(HttpMethod.GET, "/api/studyhalls/1/enrollments", ADMIN, 403);
        expect(HttpMethod.GET, "/api/owners", ADMIN, 403);
        expect(HttpMethod.POST, "/api/bookings", ADMIN, 403);
        expect(HttpMethod.GET, "/api/bookings/me", ADMIN, 403);
    }

    @Test
    void vendorsOnlyReachTheirOwnHalls() throws Exception {
        long ownerId = 9201;
        long otherId = 9202;
        approvedVendor(ownerId);
        approvedVendor(otherId);
        String owner = TestTokens.bearer(ownerId, "VENDOR");
        String other = TestTokens.bearer(otherId, "VENDOR");
        long hallId = id(send(post("/api/studyhalls"), owner, """
                {"name":"RBAC Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "openingTime":"06:00","closingTime":"22:00"}""")
                .andExpect(status().isCreated()), "id");

        send(get("/api/studyhalls/{id}/enrollments", hallId), owner, null).andExpect(status().isOk());
        send(get("/api/studyhalls/{id}/bookings", hallId), owner, null).andExpect(status().isOk());

        // Another vendor's hall looks like it does not exist.
        send(get("/api/studyhalls/{id}/enrollments", hallId), other, null).andExpect(status().isNotFound());
        send(get("/api/studyhalls/{id}/bookings", hallId), other, null).andExpect(status().isNotFound());
        send(request(HttpMethod.DELETE, "/api/studyhalls/{id}", hallId), other, null).andExpect(status().isNotFound());
        send(get("/api/vendors/me/bookings").param("studyHallId", String.valueOf(hallId)), other, null)
                .andExpect(status().isNotFound());
    }

    private void expect(HttpMethod method, String path, String bearer, int expected) throws Exception {
        int actual = send(request(method, path), bearer, method == HttpMethod.GET ? null : "{}")
                .andReturn().getResponse().getStatus();
        String who = bearer == null ? "anonymous" : bearer.equals(USER) ? "USER" : bearer.equals(VENDOR) ? "VENDOR" : "ADMIN";
        assertThat(actual).as("%s %s as %s", method, path, who).isEqualTo(expected);
    }
}
