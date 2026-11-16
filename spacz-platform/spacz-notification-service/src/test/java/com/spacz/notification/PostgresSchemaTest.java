package com.spacz.notification;

import com.spacz.notification.dto.internal.CreateNotificationRequest;
import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.job.NotificationCleanupJob;
import com.spacz.notification.repository.NotificationReceiptRepository;
import com.spacz.notification.repository.NotificationRepository;
import com.spacz.notification.security.AuthenticatedUser;
import com.spacz.notification.security.Role;
import com.spacz.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Flyway schema on real PostgreSQL: Hibernate validates the entities against it, the inbox
 * queries run on it, and the cleanup relies on its ON DELETE CASCADE. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spacz.notifications.retention=0s"
})
class PostgresSchemaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private NotificationReceiptRepository receiptRepository;
    @Autowired
    private NotificationCleanupJob cleanupJob;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void inboxReadStateAndCleanupWorkOnPostgres() {
        AuthenticatedUser student = new AuthenticatedUser(7L, null, Role.USER);
        Long personal = notificationService.create(request(7L, Audience.USER, "Personal"), "test", null).notification().getId();
        Long broadcast = notificationService.create(request(null, Audience.ALL, "Everyone"), "test", null).notification().getId();
        notificationService.create(request(null, Audience.VENDOR, "Vendors"), "test", null);

        assertThat(notificationService.inbox(student, null, false, PageRequest.of(0, 20)).content())
                .extracting(n -> n.title()).containsExactly("Everyone", "Personal");
        assertThat(notificationService.unreadCount(student)).isEqualTo(2);

        notificationService.markRead(student, broadcast);
        assertThat(notificationService.inbox(student, NotificationCategory.SYSTEM, true, PageRequest.of(0, 20)).content())
                .extracting(n -> n.id()).containsExactly(personal);
        assertThat(notificationService.markAllRead(student)).isEqualTo(1);
        assertThat(notificationService.unreadCount(student)).isZero();
        assertThat(jdbc.queryForObject("select data_json from notifications where id = ?", String.class, personal))
                .isEqualTo("{\"screen\":\"home\"}");

        cleanupJob.cleanUp();
        assertThat(notificationRepository.count()).isZero();
        assertThat(receiptRepository.count()).isZero();
    }

    private static CreateNotificationRequest request(Long userId, Audience audience, String title) {
        return new CreateNotificationRequest(userId, audience, NotificationCategory.SYSTEM, "TEST", title, "body", null,
                null, null, Map.of("screen", "home"), false, null, null);
    }
}
