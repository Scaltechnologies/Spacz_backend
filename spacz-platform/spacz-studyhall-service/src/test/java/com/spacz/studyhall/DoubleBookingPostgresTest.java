package com.spacz.studyhall;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.legacy.LegacyRefs;
import com.spacz.studyhall.dto.legacy.LegacyRequests;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.security.Role;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.LegacyPartnerService;
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
 * Runs against a real PostgreSQL 16: applies all Flyway migrations, validates the JPA mapping against
 * them, and proves that concurrent requests cannot double-book a seat — both through the service (row
 * lock) and directly in SQL (exclusion constraint) — and that the layout constraints hold. Skipped without Docker.
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
    private LegacyPartnerService legacyService;
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
                                      unit_price, base_amount, total_price)
                values (?, ?, ?, ?, ?, ?, ?, 100, 100, 100)""";
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

    @Test
    void layoutCellsStayConsistentWithSeatsBlocksAndFloors() {
        long seatId = liveHallWithOneSeat(702L);
        long blockId = jdbc.queryForObject("select block_id from seats where id = ?", Long.class, seatId);
        long hallId = jdbc.queryForObject("select study_hall_id from seats where id = ?", Long.class, seatId);
        assertThat(count("select count(*) from blocks b join floors f on f.id = b.floor_id and f.study_hall_id = b.study_hall_id"
                + " where b.id = ?", blockId)).isEqualTo(1);
        assertThat(count("select count(*) from layout_cells where seat_id = ? and layout_id = ? and cell_type = 'SEAT'"
                + " and row_index = 1 and column_index = 1", seatId, blockId)).isEqualTo(1);

        // Only SEAT cells reference a seat; a cell sits at its seat's position; a floor belongs to the block's hall.
        assertThatThrownBy(() -> jdbc.update("insert into layout_cells (layout_id, row_index, column_index, cell_type)"
                + " values (?, 2, 1, 'SEAT')", blockId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into layout_cells (layout_id, row_index, column_index, cell_type, seat_id)"
                + " values (?, 2, 1, 'WALKWAY', ?)", blockId, seatId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update layout_cells set row_index = 2 where seat_id = ?", seatId))
                .isInstanceOf(DataIntegrityViolationException.class);
        long otherSeat = liveHallWithOneSeat(703L);
        long otherFloor = jdbc.queryForObject("select b.floor_id from blocks b join seats s on s.block_id = b.id"
                + " where s.id = ?", Long.class, otherSeat);
        assertThatThrownBy(() -> jdbc.update("update blocks set floor_id = ? where id = ?", otherFloor, blockId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into layout_cells (layout_id, row_index, column_index, cell_type, seat_id)"
                + " values (?, 1, 1, 'SEAT', ?)", blockId, otherSeat)).isInstanceOf(DataIntegrityViolationException.class);

        // Legacy API: a seat moved to another block takes its cell along; a block moved to another hall
        // lands on that hall's (new) ground floor.
        long target = seatingService.createBlock(702L, hallId, new CreateBlockRequest("Two", 1, 1, "T", null, null, null,
                null, null, null, null)).id();
        legacyService.updateSeat(702L, seatId, new LegacyRequests.SeatRequest(null, new LegacyRefs.BlockRef(target), false, null));
        assertThat(count("select count(*) from layout_cells where seat_id = ? and layout_id = ? and row_index = 2", seatId, target))
                .isEqualTo(1);
        long secondHall = hallService.create(new AuthenticatedUser(702L, "v702@test.local", Role.VENDOR),
                new CreateStudyHallRequest("Second 702", null, "Road", "Hyderabad", "Telangana", null, null, null, null,
                        null, new BigDecimal("90.00"), null, null, LocalTime.of(6, 0), LocalTime.of(22, 0))).id();
        legacyService.updateBlock(702L, target, new LegacyRequests.BlockRequest(null, new LegacyRefs.PropertyRef(secondHall),
                null, null));
        assertThat(count("select count(*) from blocks b join floors f on f.id = b.floor_id where b.id = ?"
                + " and f.study_hall_id = ? and f.name = 'Ground Floor'", target, secondHall)).isEqualTo(1);

        // Deleting a seat deletes its cell.
        jdbc.update("delete from seats where id = ?", seatId);
        assertThat(count("select count(*) from layout_cells where seat_id = ?", seatId)).isZero();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
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
                new CreateBlockRequest("Solo", 1, 1, null, null, null, null, null, null, null, null)).seats().get(0).id();
        hallService.submitForApproval(vendor, hallId);
        adminService.applyAction(hallId, StatusAction.APPROVE, null);
        return seatId;
    }
}
