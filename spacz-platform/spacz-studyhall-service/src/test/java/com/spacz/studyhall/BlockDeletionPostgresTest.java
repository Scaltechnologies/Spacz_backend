package com.spacz.studyhall;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.enrollment.CreateEnrollmentRequest;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.FixtureRequest;
import com.spacz.studyhall.dto.seat.LayoutFeatureRequest;
import com.spacz.studyhall.dto.seat.UpdateLayoutRequest;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.FixturePosition;
import com.spacz.studyhall.entity.FixtureType;
import com.spacz.studyhall.entity.LayoutCellType;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.security.Role;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.StudyHallAdminService;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.VendorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Block deletion against the real schema (Flyway V1-V8, its foreign keys and cascades): the bulk delete
 * removes the block with its layout, cells, fixtures, seats and amenity links, leaves nothing orphaned,
 * keeps refusing blocks with booking or enrollment history, and handles a full 60 x 60 block quickly.
 * Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spacz.security.jwt.secret=test-only-jwt-secret-for-automated-tests-0123456789",
        "spacz.security.internal.api-key=test-internal-api-key-0123456789",
        "spacz.booking.lifecycle-interval-ms=3600000"
})
class BlockDeletionPostgresTest {

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
    private EnrollmentService enrollmentService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @AfterEach
    void noOrphansAnywhere() {
        assertThat(count("select count(*) from layout_cells c where not exists (select 1 from block_layouts l where l.block_id = c.layout_id)")).isZero();
        assertThat(count("select count(*) from layout_cells c where c.seat_id is not null and not exists (select 1 from seats s where s.id = c.seat_id)")).isZero();
        assertThat(count("select count(*) from layout_fixtures f where not exists (select 1 from block_layouts l where l.block_id = f.layout_id)")).isZero();
        assertThat(count("select count(*) from block_layouts l where not exists (select 1 from blocks b where b.id = l.block_id)")).isZero();
        assertThat(count("select count(*) from seats s where not exists (select 1 from blocks b where b.id = s.block_id)")).isZero();
        assertThat(count("select count(*) from block_amenities a where not exists (select 1 from blocks b where b.id = a.block_id)")).isZero();
        assertThat(count("select count(*) from seats s where not exists (select 1 from layout_cells c where c.seat_id = s.id)")).isZero();
    }

    @Test
    void deletesAnEmptyBlockWithItsFeaturesAndFixtures() {
        long vendorId = 801L;
        long hallId = liveHall(vendorId);
        BlockResponse block = seatingService.createBlock(vendorId, hallId, template("Empty", "s TABLE"));
        seatingService.updateLayout(vendorId, hallId, block.id(), new UpdateLayoutRequest(
                List.of(new LayoutFeatureRequest(1, 2, LayoutCellType.TABLE, null, null)), null, null,
                List.of(new FixtureRequest(FixtureType.AC, FixturePosition.CENTER, null, Direction.CENTER, "AC"))));
        seatingService.deleteSeat(vendorId, hallId, block.seats().get(0).id());
        assertThat(count("select count(*) from seats where block_id = ?", block.id())).isZero();
        assertThat(count("select count(*) from layout_fixtures where layout_id = ?", block.id())).isEqualTo(1);

        seatingService.deleteBlock(vendorId, hallId, block.id());

        assertBlockGone(block.id());
    }

    @Test
    void deletesABlockWhoseSeatsHaveNoHistoryAndLeavesOtherBlocksUntouched() {
        long vendorId = 802L;
        long hallId = liveHall(vendorId);
        long ac = count("select id from amenities where code = 'AC'");
        BlockResponse kept = seatingService.createBlock(vendorId, hallId, template("Kept", "s s\ns s"));
        BlockResponse block = seatingService.createBlock(vendorId, hallId, new CreateBlockRequest("Gone", null, null, "GN",
                null, null, new BigDecimal("150.00"), new BigDecimal("2500.00"), Set.of(ac), null,
                "s s ENTRANCE@SOUTH s\nWALKWAY . PILLAR s\ns s s s"));
        seatingService.updateLayout(vendorId, hallId, block.id(), new UpdateLayoutRequest(
                List.of(new LayoutFeatureRequest(1, 3, LayoutCellType.ENTRANCE, null, Direction.SOUTH),
                        new LayoutFeatureRequest(2, 1, LayoutCellType.WALKWAY, null, null),
                        new LayoutFeatureRequest(2, 3, LayoutCellType.PILLAR, null, null)), null, null,
                List.of(new FixtureRequest(FixtureType.AC, FixturePosition.NORTH, 2, Direction.SOUTH, "Split AC"))));
        assertThat(count("select count(*) from seats where block_id = ?", block.id())).isEqualTo(8);
        assertThat(count("select count(*) from block_amenities where block_id = ?", block.id())).isEqualTo(1);
        List<Long> keptSeats = jdbc.queryForList("select id from seats where block_id = ? order by id", Long.class, kept.id());

        seatingService.deleteBlock(vendorId, hallId, block.id());

        assertBlockGone(block.id());
        assertThat(jdbc.queryForList("select id from seats where block_id = ? order by id", Long.class, kept.id())).isEqualTo(keptSeats);
        assertThat(count("select count(*) from layout_cells where layout_id = ? and cell_type = 'SEAT'", kept.id())).isEqualTo(4);
    }

    @Test
    void refusesBlocksWithEnrollmentOrBookingHistoryAndKeepsEverything() {
        long vendorId = 803L;
        long hallId = liveHall(vendorId);
        BlockResponse enrolled = seatingService.createBlock(vendorId, hallId, template("Enrolled", "s s s"));
        BlockResponse booked = seatingService.createBlock(vendorId, hallId, template("Booked", "s s"));
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
        // a walk-in on the LAST seat: the old per-seat check had to reach it, the block query must too
        long lastSeat = enrolled.seats().get(2).id();
        enrollmentService.create(vendorId, hallId, new CreateEnrollmentRequest(null, "Walk In", "+919811100002", null,
                null, lastSeat, null, start, null, null));
        bookingService.create(5_001L, new CreateBookingRequest(hallId, booked.seats().get(1).id(), BookingPlan.DAILY,
                start, start.plusDays(1), null, null));
        long seatsBefore = count("select count(*) from seats where study_hall_id = ?", hallId);
        long cellsBefore = count("select count(*) from layout_cells c join seats s on s.id = c.seat_id where s.study_hall_id = ?", hallId);

        for (BlockResponse block : List.of(enrolled, booked)) {
            assertThatThrownBy(() -> seatingService.deleteBlock(vendorId, hallId, block.id()))
                    .isInstanceOf(ConflictException.class)
                    .satisfies(e -> assertThat(((ConflictException) e).getErrorCode()).isEqualTo("BLOCK_IN_USE"));
            assertThat(count("select count(*) from blocks where id = ?", block.id())).isEqualTo(1);
            assertThat(count("select count(*) from block_layouts where block_id = ?", block.id())).isEqualTo(1);
        }
        assertThat(count("select count(*) from seats where study_hall_id = ?", hallId)).isEqualTo(seatsBefore);
        assertThat(count("select count(*) from layout_cells c join seats s on s.id = c.seat_id where s.study_hall_id = ?", hallId)).isEqualTo(cellsBefore);
        assertThat(count("select count(*) from enrollments where seat_id = ?", lastSeat)).isEqualTo(1);
        assertThat(count("select count(*) from bookings where seat_id = ?", booked.seats().get(1).id())).isEqualTo(1);
    }

    @Test
    void deletesAFullSixtyBySixtyBlockQuickly() {
        long vendorId = 804L;
        long hallId = liveHall(vendorId);
        BlockResponse block = seatingService.createBlock(vendorId, hallId,
                new CreateBlockRequest("Max", 60, 60, "MX", null, null, null, null, null, null, null));
        assertThat(count("select count(*) from seats where block_id = ?", block.id())).isEqualTo(3600);
        assertThat(count("select count(*) from layout_cells where layout_id = ?", block.id())).isEqualTo(3600);

        long started = System.nanoTime();
        seatingService.deleteBlock(vendorId, hallId, block.id());
        long millis = (System.nanoTime() - started) / 1_000_000;

        assertBlockGone(block.id());
        // per-seat deletion took ~16 s; the gateway gives up after 20 s
        assertThat(millis).as("deleting 3,600 seats took %d ms", millis).isLessThan(5_000);
    }

    private void assertBlockGone(long blockId) {
        assertThat(count("select count(*) from blocks where id = ?", blockId)).isZero();
        assertThat(count("select count(*) from block_layouts where block_id = ?", blockId)).isZero();
        assertThat(count("select count(*) from layout_cells where layout_id = ?", blockId)).isZero();
        assertThat(count("select count(*) from layout_fixtures where layout_id = ?", blockId)).isZero();
        assertThat(count("select count(*) from seats where block_id = ?", blockId)).isZero();
        assertThat(count("select count(*) from block_amenities where block_id = ?", blockId)).isZero();
    }

    private static CreateBlockRequest template(String name, String layoutTemplate) {
        return new CreateBlockRequest(name, null, null, name.substring(0, 2).toUpperCase(), null, null, null, null, null,
                null, layoutTemplate);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long liveHall(long vendorId) {
        AuthenticatedUser vendor = new AuthenticatedUser(vendorId, "v" + vendorId + "@test.local", Role.VENDOR);
        vendorService.createFromRegistration(new CreateVendorProfileRequest(vendorId, vendor.email(), null, null, null, null));
        vendorService.updateMine(vendor, new VendorProfileRequest("Deletion Hall", "Owner", "+919999999999", null,
                "Road", "Hyderabad", null, null, null, null, null));
        vendorService.submit(vendor);
        vendorService.applyAction(vendorId, StatusAction.APPROVE, null);
        long hallId = hallService.create(vendor, new CreateStudyHallRequest("Hall " + vendorId, null, "Road", "Hyderabad",
                "Telangana", null, null, null, null, null, new BigDecimal("120.00"), null, null,
                LocalTime.of(6, 0), LocalTime.of(22, 0))).id();
        seatingService.createBlock(vendorId, hallId, new CreateBlockRequest("Solo", 1, 1, null, null, null, null, null,
                null, null, null));
        hallService.submitForApproval(vendor, hallId);
        adminService.applyAction(hallId, StatusAction.APPROVE, null);
        return hallId;
    }
}
