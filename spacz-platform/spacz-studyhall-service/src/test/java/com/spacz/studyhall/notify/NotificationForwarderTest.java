package com.spacz.studyhall.notify;

import com.spacz.studyhall.config.SecurityProperties;
import com.spacz.studyhall.config.ServiceClientFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivery to notification-service: endpoint, internal key, JSON body, retries on 5xx only.
 */
class NotificationForwarderTest {

    private static final String KEY = "test-internal-api-key-0123456789";

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int[] statuses;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/notifications", exchange -> {
            int call = calls.getAndIncrement();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            int status = statuses[Math.min(call, statuses.length - 1)];
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsTheEventWithTheInternalKey() {
        statuses = new int[]{201};
        forwarder(true).forward(NotificationEvent.toUser(42L, "USER", "BOOKING", "BOOKING_CONFIRMED", "Booking confirmed", "Seat A1")
                .about("BOOKING", 7L).with("bookingId", 7L));

        assertThat(calls).hasValue(1);
        assertThat(keys).containsExactly(KEY);
        assertThat(bodies.get(0)).contains("\"recipientUserId\":42", "\"audience\":\"USER\"", "\"type\":\"BOOKING_CONFIRMED\"",
                "\"entityId\":7", "\"bookingId\":\"7\"", "\"push\":true", "\"dedupKey\":\"");
    }

    @Test
    void retriesServerErrorsWithTheSameDedupKeyButNotClientErrors() {
        statuses = new int[]{503, 502, 201};
        forwarder(true).forward(NotificationEvent.toAdmins("REQUEST", "VENDOR_APPROVAL_REQUESTED", "t", "b"));
        assertThat(calls).hasValue(3);
        assertThat(bodies).allMatch(body -> body.equals(bodies.get(0)));

        calls.set(0);
        statuses = new int[]{400};
        forwarder(true).forward(NotificationEvent.toAdmins("REQUEST", "X", "t", "b"));
        assertThat(calls).hasValue(1);

        calls.set(0);
        statuses = new int[]{500};
        forwarder(true).forward(NotificationEvent.toAdmins("REQUEST", "X", "t", "b"));
        assertThat(calls).hasValue(NotificationForwarder.ATTEMPTS);
    }

    @Test
    void doesNothingWhenDisabled() {
        statuses = new int[]{201};
        forwarder(false).forward(NotificationEvent.toAdmins("REQUEST", "X", "t", "b"));
        assertThat(calls).hasValue(0);
    }

    private NotificationForwarder forwarder(boolean enabled) {
        ServiceClientFactory factory = new ServiceClientFactory(RestClient.builder(),
                new SecurityProperties(new SecurityProperties.Jwt("x".repeat(32), "spacz-auth"), new SecurityProperties.Internal(KEY)),
                "spacz-studyhall-service", Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new NotificationForwarder(factory, "http://127.0.0.1:" + server.getAddress().getPort(), enabled, 1);
    }
}
