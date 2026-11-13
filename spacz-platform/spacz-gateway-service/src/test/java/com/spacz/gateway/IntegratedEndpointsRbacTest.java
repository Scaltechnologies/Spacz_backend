package com.spacz.gateway;

import com.spacz.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role matrix at the gateway for every endpoint added by the advanced-backend integration (floors and
 * layouts, hall images, booking quotes, offers / platform updates / notifications, user documents and
 * feedback). For each endpoint and each caller (anonymous, USER, VENDOR, ADMIN) the gateway must either
 * reject (401 without a token, 403 with the wrong role) or let the request through, which here ends in
 * a 5xx because the downstream services point at a closed port. The expected roles mirror the
 * services' own rules (@PreAuthorize / SecurityConfig), so the edge and the services agree.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spacz.gateway.jwt-secret=" + TestTokens.SECRET,
        "spacz.gateway.jwt-issuer=" + TestTokens.ISSUER,
        "spacz.gateway.cors-allowed-origins=http://localhost:5173",
        "spring.cloud.gateway.httpclient.connect-timeout=500",
        "AUTH_SERVICE_URL=http://127.0.0.1:1",
        "USER_SERVICE_URL=http://127.0.0.1:1",
        "STUDYHALL_SERVICE_URL=http://127.0.0.1:1",
        "ADMIN_SERVICE_URL=http://127.0.0.1:1"
})
@AutoConfigureWebTestClient(timeout = "10s")
class IntegratedEndpointsRbacTest {

    private static final Set<String> PUBLIC = Set.of("ANONYMOUS", "USER", "VENDOR", "ADMIN");
    private static final Set<String> SIGNED_IN = Set.of("USER", "VENDOR", "ADMIN");
    private static final Set<String> USER = Set.of("USER");
    private static final Set<String> VENDOR = Set.of("VENDOR");
    private static final Set<String> ADMIN = Set.of("ADMIN");
    private static final Set<String> USER_OR_ADMIN = Set.of("USER", "ADMIN");

    private record Case(HttpMethod method, String path, Set<String> allowed) {
    }

    private static final List<Case> CASES = List.of(
            // studyhall-service: public reads
            new Case(HttpMethod.GET, "/api/studyhalls/locations", PUBLIC),
            new Case(HttpMethod.GET, "/api/studyhalls/3/layout", PUBLIC),
            new Case(HttpMethod.GET, "/api/studyhalls/media/3/2b1f.jpg", PUBLIC),
            // studyhall-service: floors, layouts and images are vendor-only (reads included)
            new Case(HttpMethod.GET, "/api/studyhalls/3/floors", VENDOR),
            new Case(HttpMethod.POST, "/api/studyhalls/3/floors", VENDOR),
            new Case(HttpMethod.PUT, "/api/studyhalls/3/floors/4", VENDOR),
            new Case(HttpMethod.DELETE, "/api/studyhalls/3/floors/4", VENDOR),
            new Case(HttpMethod.PUT, "/api/studyhalls/3/blocks/5/layout", VENDOR),
            new Case(HttpMethod.POST, "/api/studyhalls/3/images/upload", VENDOR),
            new Case(HttpMethod.DELETE, "/api/studyhalls/3/images/9", VENDOR),
            // studyhall-service: price quote is a student action
            new Case(HttpMethod.POST, "/api/bookings/quote", USER),
            // admin-service: student content
            new Case(HttpMethod.GET, "/api/offers", PUBLIC),
            new Case(HttpMethod.GET, "/api/notifications", SIGNED_IN),
            // admin-service: offer and platform-update management
            new Case(HttpMethod.GET, "/api/admin/offers", ADMIN),
            new Case(HttpMethod.POST, "/api/admin/offers", ADMIN),
            new Case(HttpMethod.PUT, "/api/admin/offers/1", ADMIN),
            new Case(HttpMethod.PATCH, "/api/admin/offers/1/status", ADMIN),
            new Case(HttpMethod.DELETE, "/api/admin/offers/1", ADMIN),
            new Case(HttpMethod.GET, "/api/admin/updates", ADMIN),
            new Case(HttpMethod.POST, "/api/admin/updates", ADMIN),
            new Case(HttpMethod.PUT, "/api/admin/updates/1", ADMIN),
            new Case(HttpMethod.PATCH, "/api/admin/updates/1/status", ADMIN),
            new Case(HttpMethod.DELETE, "/api/admin/updates/1", ADMIN),
            // user-service: own documents and feedback (user-service allows USER and ADMIN on /api/users/**)
            new Case(HttpMethod.GET, "/api/users/me/documents", USER_OR_ADMIN),
            new Case(HttpMethod.PUT, "/api/users/me/documents/AADHAAR_FRONT", USER_OR_ADMIN),
            new Case(HttpMethod.GET, "/api/users/me/documents/AADHAAR_FRONT/file", USER_OR_ADMIN),
            new Case(HttpMethod.GET, "/api/users/me/feedback", USER_OR_ADMIN),
            new Case(HttpMethod.POST, "/api/users/me/feedback", USER_OR_ADMIN));

    @Autowired
    private WebTestClient client;

    @Test
    void everyIntegratedEndpointIsCheckedAtTheEdgeForEveryRole() {
        List<String> mismatches = new ArrayList<>();
        for (Case c : CASES) {
            for (String caller : List.of("ANONYMOUS", "USER", "VENDOR", "ADMIN")) {
                int status = call(c, caller);
                String outcome;
                if (c.allowed().contains(caller)) {
                    outcome = status >= 500 ? null : "expected to pass the gateway, got " + status;
                } else {
                    int expected = caller.equals("ANONYMOUS") ? 401 : 403;
                    outcome = status == expected ? null : "expected " + expected + ", got " + status;
                }
                if (outcome != null) {
                    mismatches.add(c.method() + " " + c.path() + " as " + caller + ": " + outcome);
                }
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void internalOfferFeedIsNeverExposed() {
        for (String caller : List.of("USER", "VENDOR", "ADMIN")) {
            assertThat(call(new Case(HttpMethod.GET, "/internal/offers/live", Set.of()), caller)).isEqualTo(403);
        }
    }

    private int call(Case c, String caller) {
        WebTestClient.RequestBodySpec request = client.method(c.method()).uri(c.path());
        if (!caller.equals("ANONYMOUS")) {
            request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(caller.equals("ADMIN") ? 1 : 5, caller));
        }
        WebTestClient.RequestHeadersSpec<?> ready = c.method() == HttpMethod.GET || c.method() == HttpMethod.DELETE
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
        return ready.exchange().returnResult(String.class).getStatus().value();
    }
}
