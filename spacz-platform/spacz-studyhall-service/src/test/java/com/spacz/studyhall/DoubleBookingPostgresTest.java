package com.spacz.studyhall;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.security.Role;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.StudyHallAdminService;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.VendorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL 16: applies all Flyway migrations (V1..V5), validates the JPA
 * mapping against them, and proves that concurrent requests cannot double-book a seat — both
 * through the service (row lock) and directly in SQL (exclusion constraint). Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spacz.security.jwt.secret=test-only-jwt-secret-for-automated-tests-0123456789",
        "spacz.security.internal.api-key=test-internal-api-key-0123456789",
        "spacz.booking.lifecycle-interval-ms=3600000"
})
class DoubleBookingPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VendorService vendorService;
    @Autowired
    private StudyHallManagementService hallService;
    @Autowired
    private SeatingService seatingService;
    @Autowired
    private StudyHallAdminService adminService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void concurrentRequestsForTheSameSeatProduceExactlyOneBooking() throws Exception {
        long seatId = liveHallWithOneSeat(700L);
        long hallId = jdbc.queryForObject("select study_hall_id from seats where id = ?", Long.class, seatId);
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(3);

        int attempts = 12;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            long userId = 10_000L + i;
            LocalDate from = start.plusDays(i % 3);
            Callable<Boolean> attempt = () -> {
                go.await();
                try {
                    bookingService.create(userId, new CreateBookingRequest(hallId, seatId, BookingPlan.DAILY, from,
                            from.plusDays(3), null, null));
                    return true;
                } catch (ConflictException ex) {
                    return false;
                }
            };
            results.add(pool.submit(attempt));
        }
        go.countDown();
        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from bookings where seat_id = ? and status in ('PENDING','CONFIRMED')", Long.class, seatId))
                .isEqualTo(1L);
    }

    @Test
    void exclusionConstraintBlocksOverlapsEvenWhenTheServiceIsBypassed() {
        long seatId = liveHallWithOneSeat(701L);
        long hallId = jdbc.queryForObject("select study_hall_id from seats where id = ?", Long.class, seatId);
        String insert = """
                insert into bookings (booking_reference, user_id, study_hall_id, seat_id, start_date, end_date, status,
                                      unit_price, total_price)
                values (?, ?, ?, ?, ?, ?, ?, 100, 100)""";
        LocalDate day = LocalDate.of(2030, 1, 10);

        jdbc.update(insert, "SPZ-SQL-1", 1L, hallId, seatId, day, day.plusDays(5), "CONFIRMED");
        assertThatThrownBy(() -> jdbc.update(insert, "SPZ-SQL-2", 2L, hallId, seatId, day.plusDays(5), day.plusDays(6), "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, "SPZ-SQL-3", 3L, hallId, seatId, day.plusDays(2), day.plusDays(3), "CANCELLED");
        jdbc.update(insert, "SPZ-SQL-4", 4L, hallId, seatId, day.plusDays(6), day.plusDays(8), "CONFIRMED");
    }

    @Test
    void programCatalogWasSeededWithStableIds() {
        assertThat(jdbc.queryForObject("select code from programs where id = 1", String.class)).isEqualTo("UPSC_CSE");
        assertThat(jdbc.queryForObject("select count(*) from programs where code = 'EAMCET'", Long.class)).isEqualTo(1L);
    }

    private long liveHallWithOneSeat(long vendorId) {
        AuthenticatedUser vendor = new AuthenticatedUser(vendorId, "v" + vendorId + "@test.local", Role.VENDOR);
        vendorService.createFromRegistration(new CreateVendorProfileRequest(vendorId, vendor.email(), null, null, null, null));
        vendorService.updateMine(vendor, new VendorProfileRequest("Concurrency Hall", "Owner", "+919999999999", null,
                "Road", "Hyderabad", null, null, null, null, null));
        vendorService.submit(vendor);
        vendorService.applyAction(vendorId, StatusAction.APPROVE, null);
        long hallId = hallService.create(vendor, new CreateStudyHallRequest("Hall " + vendorId, null, "Road", "Hyderabad",
                "Telangana", null, null, null, null, null, new BigDecimal("120.00"), null, null,
                LocalTime.of(6, 0), LocalTime.of(22, 0))).id();
        long seatId = seatingService.createBlock(vendorId, hallId,
                new CreateBlockRequest("Solo", 1, 1, null, null, null, null, null, null)).seats().get(0).id();
        hallService.submitForApproval(vendor, hallId);
        adminService.applyAction(hallId, StatusAction.APPROVE, null);
        return seatId;
    }
}
