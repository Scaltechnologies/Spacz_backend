package com.spacz.gateway;

import com.spacz.gateway.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Edge security of the gateway. Downstream services point at a closed port, so a request that
 * passes the gateway's checks ends in a 5xx from routing — never in a 401/403.
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
class GatewaySecurityTest {

    @Autowired
    private WebTestClient client;
    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void protectedRouteWithoutTokenIsRejectedAtTheEdge() {
        client.get().uri("/api/bookings/me").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody().jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void expiredOrForgedTokensAreRejected() {
        String expired = TestTokens.token(1, "USER", TestTokens.SECRET, TestTokens.ISSUER, Duration.ofMinutes(-5));
        client.get().uri("/api/bookings/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired).exchange()
                .expectStatus().isUnauthorized();
        String forged = TestTokens.token(1, "ADMIN", "attacker-chosen-secret-attacker-chosen-secret", TestTokens.ISSUER,
                Duration.ofMinutes(5));
        client.get().uri("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void wrongRoleIsForbidden() {
        client.get().uri("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "USER")).exchange()
                .expectStatus().isForbidden();
        // GET /api/vendors/{id} is public (vendor cards), so the edge rule is tested with a vendor-only write.
        client.put().uri("/api/vendors/me").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "USER"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void validAdminTokenPassesTheEdge() {
        client.get().uri("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(1, "ADMIN")).exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void publicRoutesNeedNoToken() {
        client.get().uri("/api/studyhalls?city=Hyderabad").exchange().expectStatus().is5xxServerError();
        client.get().uri("/api/amenities").exchange().expectStatus().is5xxServerError();
        client.post().uri("/api/auth/otp/request").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}").exchange().expectStatus().is5xxServerError();
        client.get().uri("/api/programs").exchange().expectStatus().is5xxServerError();
        client.post().uri("/api/auth/login").header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{}").exchange().expectStatus().is5xxServerError();
    }

    @Test
    void catalogWritesNeedAdminAndLegacyPartnerApiNeedsVendor() {
        client.post().uri("/api/programs").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "VENDOR"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}").exchange().expectStatus().isForbidden();
        client.get().uri("/api/owners").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "USER")).exchange()
                .expectStatus().isForbidden();
        client.get().uri("/api/owners").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "VENDOR")).exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void internalEndpointsAreNeverExposed() {
        client.get().uri("/internal/accounts/stats").header("X-Internal-Api-Key", "guess")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(1, "ADMIN")).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void correlationIdIsPreservedOrGenerated() {
        client.get().uri("/api/bookings/me").header("X-Correlation-Id", "frontend-req-42").exchange()
                .expectHeader().valueEquals("X-Correlation-Id", "frontend-req-42");
        String generated = client.get().uri("/api/bookings/me").header("X-Correlation-Id", "bad id with spaces!").exchange()
                .returnResult(Void.class).getResponseHeaders().getFirst("X-Correlation-Id");
        assertThat(generated).isNotEqualTo("bad id with spaces!").hasSize(36);
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() {
        MockServerHttpResponse allowed = preflightFrom("http://localhost:5173");
        assertThat(allowed.getStatusCode()).isNull();
        assertThat(allowed.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(preflightFrom("https://evil.example").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Applies the gateway's CORS configuration to a preflight request. (The test HTTP client sends
     * preflights without a resolvable host, so the processor is exercised directly here.)
     */
    private MockServerHttpResponse preflightFrom(String origin) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("http://gateway.local/api/bookings")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"));
        new DefaultCorsProcessor().process(corsConfigurationSource.getCorsConfiguration(exchange), exchange);
        return exchange.getResponse();
    }
}
