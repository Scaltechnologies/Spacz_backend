package com.spacz.gateway;

import com.spacz.gateway.support.TestTokens;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * GET /api/notifications (offers/updates feed) stays on admin-service; everything below
 * /api/notifications/ goes to notification-service. Each stub answers with its own name.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spacz.gateway.jwt-secret=" + TestTokens.SECRET,
        "spacz.gateway.jwt-issuer=" + TestTokens.ISSUER,
        "spacz.gateway.cors-allowed-origins=http://localhost:5173"
})
@AutoConfigureWebTestClient(timeout = "10s")
class NotificationRoutingTest {

    private static final HttpServer ADMIN = stub("admin-service");
    private static final HttpServer NOTIFICATION = stub("notification-service");

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("ADMIN_SERVICE_URL", () -> "http://127.0.0.1:" + ADMIN.getAddress().getPort());
        registry.add("NOTIFICATION_SERVICE_URL", () -> "http://127.0.0.1:" + NOTIFICATION.getAddress().getPort());
    }

    @AfterAll
    static void stopStubs() {
        ADMIN.stop(0);
        NOTIFICATION.stop(0);
    }

    @Test
    void feedStaysOnAdminServiceAndEverythingBelowGoesToNotificationService() {
        String student = TestTokens.bearer(5, "USER");
        expect("/api/notifications", student, "admin-service");
        expect("/api/notifications/inbox", student, "notification-service");
        expect("/api/notifications/inbox/unread-count", student, "notification-service");
        expect("/api/notifications/categories", null, "notification-service");
        expect("/api/notifications/admin/sent", TestTokens.bearer(1, "ADMIN"), "notification-service");
        expect("/api/offers", null, "admin-service");
    }

    private void expect(String path, String bearer, String service) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri(path);
        if (bearer != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        request.exchange().expectStatus().isOk().expectBody(String.class).isEqualTo(service);
    }

    private static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = name.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
