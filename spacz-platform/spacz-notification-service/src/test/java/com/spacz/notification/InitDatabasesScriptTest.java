package com.spacz.notification;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * docker/postgres/init-databases.sh, which deploy.sh re-runs before every deploy to create the
 * databases of newly added services: running it twice must work and must not change anything that
 * exists (in particular not the passwords). Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class InitDatabasesScriptTest {

    private static final Path SCRIPT = Path.of("..", "docker", "postgres", "init-databases.sh");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath(SCRIPT), "/tmp/init-databases.sh");

    @Test
    void createsEveryServiceDatabaseAndIsSafeToRunAgain() throws Exception {
        ExecResult first = run("notif-pass-1");
        assertThat(first.getExitCode()).as(first.getStderr()).isZero();
        assertThat(first.getStdout()).contains("database spacz_notification owned by spacz_notification is ready");

        ExecResult second = run("a-different-password");
        assertThat(second.getExitCode()).as(second.getStderr()).isZero();

        // the first password is kept; each role reaches only its own database
        try (Connection ignored = connect("spacz_notification", "spacz_notification", "notif-pass-1")) {
            assertThat(ignored.isValid(2)).isTrue();
        }
        assertThatThrownBy(() -> connect("spacz_notification", "spacz_notification", "a-different-password"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> connect("spacz_auth", "spacz_notification", "notif-pass-1"))
                .isInstanceOf(SQLException.class);
    }

    private static ExecResult run(String notificationPassword) throws Exception {
        return POSTGRES.execInContainer("env", "POSTGRES_USER=" + POSTGRES.getUsername(),
                "AUTH_DB_PASSWORD=auth-pass", "USER_DB_PASSWORD=user-pass", "STUDYHALL_DB_PASSWORD=studyhall-pass",
                "ADMIN_DB_PASSWORD=admin-pass", "NOTIFICATION_DB_PASSWORD=" + notificationPassword,
                "bash", "/tmp/init-databases.sh");
    }

    private static Connection connect(String database, String user, String password) throws SQLException {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        return DriverManager.getConnection(url, user, password);
    }
}
