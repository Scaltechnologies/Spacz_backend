package com.spacz.studyhall;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upgrade path of an existing production database: main (before this integration) runs the
 * studyhall schema up to V5. Seeds V5-shaped data, then applies V6..latest and checks what the
 * data migrations change: V9 (booking price breakdown backfill) and V11 (DRAFT vendors whose halls
 * are awaiting approval move to PENDING) - and, just as important, what they must leave alone.
 * Also records what happens if the deploy is rolled back to a V5-only image. Skipped without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationUpgradePostgresTest {

    /** Last studyhall migration on main before the advanced backend was integrated. */
    private static final String PRODUCTION_VERSION = "5";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Map<String, Long> VENDORS = new TreeMap<>();
    private static final Map<String, Map<String, Object>> VENDORS_BEFORE = new TreeMap<>();
    private static final Map<Long, String> HALL_STATUS_BEFORE = new TreeMap<>();
    private static final List<Long> BOOKINGS = new ArrayList<>();

    @BeforeAll
    static void upgradeFromProductionSchema() throws SQLException {
        flyway("classpath:db/migration").target(MigrationVersion.fromVersion(PRODUCTION_VERSION)).load().migrate();
        try (Connection c = connect()) {
            seed(c);
            snapshot(c);
        }
        flyway("classpath:db/migration").load().migrate();
    }

    @Test
    void v9BackfillsEveryExistingBookingWithoutChangingItsPrice() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, total_price, base_amount, discount_amount, platform_charges,"
                     + " tax_amount, offer_id, offer_title FROM bookings ORDER BY id")) {
            int rows = 0;
            while (rs.next()) {
                rows++;
                BigDecimal total = rs.getBigDecimal("total_price");
                assertThat(rs.getBigDecimal("base_amount")).isEqualByComparingTo(total);
                assertThat(rs.getBigDecimal("discount_amount")).isEqualByComparingTo("0");
                assertThat(rs.getBigDecimal("platform_charges")).isEqualByComparingTo("0");
                assertThat(rs.getBigDecimal("tax_amount")).isEqualByComparingTo("0");
                assertThat(rs.getObject("offer_id")).isNull();
                assertThat(rs.getObject("offer_title")).isNull();
            }
            assertThat(rows).isEqualTo(BOOKINGS.size());
        }
        // The original totals are untouched (seeded values, including a fractional one).
        assertThat(totals()).containsExactly(new BigDecimal("150.00"), new BigDecimal("3000.00"),
                new BigDecimal("99.99"), new BigDecimal("450.50"));
    }

    @Test
    void v9ConstraintRejectsInconsistentBreakdownsAndAcceptsConsistentOnes() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            long seat = scalarLong(c, "SELECT seat_id FROM bookings ORDER BY id LIMIT 1");
            long hall = scalarLong(c, "SELECT study_hall_id FROM bookings ORDER BY id LIMIT 1");
            // 500 - 50 + 20 + 84.60 = 554.60
            insertNewBooking(c, "OK-1", hall, seat, "554.60", "500.00", "50.00", "20.00", "84.60");
            assertThatThrownBy(() -> insertNewBooking(c, "BAD-1", hall, seat, "600.00", "500.00", "0", "0", "0"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("ck_bookings_price_breakdown");
            c.rollback();
        }
    }

    @Test
    void v11SubmitsOnlyDraftVendorsWithHallsAwaitingApproval() throws SQLException {
        Map<String, Map<String, Object>> after = vendorRows();

        Map<String, Object> moved = after.get("draft-with-pending-hall");
        assertThat(moved.get("status")).isEqualTo("PENDING");
        assertThat(moved.get("submitted_at")).isNotNull();
        assertThat((Long) moved.get("version")).isEqualTo((Long) VENDORS_BEFORE.get("draft-with-pending-hall").get("version") + 1);

        Map<String, Object> movedKeepsDate = after.get("draft-with-pending-hall-and-submitted-at");
        assertThat(movedKeepsDate.get("status")).isEqualTo("PENDING");
        assertThat(movedKeepsDate.get("submitted_at"))
                .isEqualTo(VENDORS_BEFORE.get("draft-with-pending-hall-and-submitted-at").get("submitted_at"));

        // Everyone else is byte-for-byte unchanged (status, submitted_at, updated_at, version).
        for (String untouched : List.of("draft-no-halls", "draft-with-draft-hall", "draft-with-approved-hall",
                "rejected-with-pending-hall", "approved-with-active-hall", "pending-with-pending-hall")) {
            assertThat(after.get(untouched)).as(untouched).isEqualTo(VENDORS_BEFORE.get(untouched));
        }

        // V11 never touches study halls, and never makes a vendor operational (approval still needed).
        try (Connection c = connect()) {
            for (Map.Entry<Long, String> hall : HALL_STATUS_BEFORE.entrySet()) {
                assertThat(scalarString(c, "SELECT status FROM study_halls WHERE id = " + hall.getKey()))
                        .isEqualTo(hall.getValue());
            }
            assertThat(scalarLong(c, "SELECT count(*) FROM vendor_profiles WHERE status IN ('APPROVED','ACTIVE')"))
                    .isEqualTo(1);
        }
    }

    @Test
    void existingHallsGetAGroundFloorAndALayoutForEveryBlock() throws SQLException {
        try (Connection c = connect()) {
            assertThat(scalarLong(c, "SELECT count(*) FROM blocks WHERE floor_id IS NULL")).isZero();
            assertThat(scalarLong(c, "SELECT count(*) FROM blocks b WHERE NOT EXISTS"
                    + " (SELECT 1 FROM block_layouts l WHERE l.block_id = b.id)")).isZero();
            assertThat(scalarLong(c, "SELECT count(*) FROM seats s WHERE NOT EXISTS"
                    + " (SELECT 1 FROM layout_cells lc WHERE lc.seat_id = s.id)")).isZero();
        }
    }

    /**
     * Rollback risk, recorded on purpose: a V5-only (pre-integration) image still starts against the
     * upgraded schema (Flyway ignores newer applied migrations by default), but bookings it inserts
     * omit base_amount (V9, NOT NULL) and blocks omit floor_id (V6, NOT NULL) - so booking and block
     * creation fail after such a rollback.
     */
    @Test
    void rollingBackToAPreIntegrationImageStartsButCannotCreateBookings(@TempDir Path oldMigrations) throws Exception {
        for (String file : List.of("V1__initial_schema.sql", "V2__add_indexes.sql", "V3__booking_overlap_constraint.sql",
                "V4__seed_amenities.sql", "V5__blocks_programs_enrollments.sql")) {
            try (InputStream in = MigrationUpgradePostgresTest.class.getResourceAsStream("/db/migration/" + file)) {
                Files.copy(in, oldMigrations.resolve(file));
            }
        }
        // What the old image's Flyway does on startup: validate + migrate with only V1..V5 on its classpath.
        flyway("filesystem:" + oldMigrations.toAbsolutePath()).load().migrate();

        try (Connection c = connect()) {
            c.setAutoCommit(false);
            long seat = scalarLong(c, "SELECT seat_id FROM bookings ORDER BY id LIMIT 1");
            long hall = scalarLong(c, "SELECT study_hall_id FROM bookings ORDER BY id LIMIT 1");
            assertThatThrownBy(() -> insertOldBooking(c, "OLD-APP", hall, seat, "2027-02-01", "100.00"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("base_amount");
            c.rollback();
            // Same for blocks: V6 made floor_id NOT NULL, which the old image does not set.
            assertThatThrownBy(() -> insert(c, "INSERT INTO blocks (study_hall_id, name, total_rows, total_columns)"
                    + " VALUES (?, 'Old app block', 1, 1)", hall))
                    .isInstanceOf(SQLException.class).hasMessageContaining("floor_id");
            c.rollback();
        }
    }

    // ---------------------------------------------------------------- seed data (V5 schema)

    private static void seed(Connection c) throws SQLException {
        long draftNoHalls = vendor(c, "draft-no-halls", "DRAFT", null);
        long draftDraftHall = vendor(c, "draft-with-draft-hall", "DRAFT", null);
        long draftPending = vendor(c, "draft-with-pending-hall", "DRAFT", null);
        long draftPendingDated = vendor(c, "draft-with-pending-hall-and-submitted-at", "DRAFT",
                Timestamp.valueOf("2026-01-15 10:00:00"));
        long draftApproved = vendor(c, "draft-with-approved-hall", "DRAFT", null);
        long rejected = vendor(c, "rejected-with-pending-hall", "REJECTED", Timestamp.valueOf("2026-02-01 09:00:00"));
        long approved = vendor(c, "approved-with-active-hall", "APPROVED", Timestamp.valueOf("2026-01-01 09:00:00"));
        long pending = vendor(c, "pending-with-pending-hall", "PENDING", Timestamp.valueOf("2026-03-01 09:00:00"));

        hall(c, draftDraftHall, "DRAFT");
        hall(c, draftPending, "PENDING_APPROVAL");
        hall(c, draftPending, "DRAFT");
        hall(c, draftPendingDated, "PENDING_APPROVAL");
        hall(c, draftApproved, "APPROVED");
        hall(c, rejected, "PENDING_APPROVAL");
        hall(c, pending, "PENDING_APPROVAL");
        long activeHall = hall(c, approved, "ACTIVE");

        long block = insert(c, "INSERT INTO blocks (study_hall_id, name, total_rows, total_columns) VALUES (?, 'Main', 2, 2)",
                activeHall);
        long seat1 = seat(c, activeHall, block, "A1", 1, 1);
        long seat2 = seat(c, activeHall, block, "A2", 1, 2);
        seat(c, activeHall, block, "B1", 2, 1);

        BOOKINGS.add(oldBooking(c, "LEGACY-1", activeHall, seat1, "2026-04-01", "2026-04-01", "COMPLETED", "DAILY", "150.00"));
        BOOKINGS.add(oldBooking(c, "LEGACY-2", activeHall, seat2, "2026-05-01", "2026-05-31", "CONFIRMED", "MONTHLY", "3000.00"));
        BOOKINGS.add(oldBooking(c, "LEGACY-3", activeHall, seat1, "2026-06-10", "2026-06-10", "CANCELLED", "DAILY", "99.99"));
        BOOKINGS.add(oldBooking(c, "LEGACY-4", activeHall, seat1, "2026-12-01", "2026-12-03", "PENDING", "DAILY", "450.50"));
    }

    private static long vendor(Connection c, String key, String status, Timestamp submittedAt) throws SQLException {
        long id = insert(c, "INSERT INTO vendor_profiles (vendor_id, business_name, status, submitted_at, version)"
                + " VALUES (?, ?, ?, ?, 3)", 1000L + VENDORS.size(), key, status, submittedAt);
        VENDORS.put(key, id);
        return id;
    }

    private static long hall(Connection c, long vendorProfileId, String status) throws SQLException {
        long id = insert(c, "INSERT INTO study_halls (vendor_profile_id, name, city, state, price_per_day, status)"
                + " VALUES (?, 'Hall', 'Hyderabad', 'Telangana', 100.00, ?)", vendorProfileId, status);
        HALL_STATUS_BEFORE.put(id, status);
        return id;
    }

    private static long seat(Connection c, long hall, long block, String number, int row, int column) throws SQLException {
        return insert(c, "INSERT INTO seats (study_hall_id, block_id, seat_number, row_index, column_index, seat_type, status)"
                + " VALUES (?, ?, ?, ?, ?, 'STANDARD', 'AVAILABLE')", hall, block, number, row, column);
    }

    private static long oldBooking(Connection c, String ref, long hall, long seat, String start, String end,
                                   String status, String plan, String total) throws SQLException {
        return insert(c, "INSERT INTO bookings (booking_reference, user_id, study_hall_id, seat_id, start_date, end_date,"
                        + " status, plan, unit_price, total_price) VALUES (?, 7, ?, ?, ?::date, ?::date, ?, ?, ?, ?)",
                ref, hall, seat, start, end, status, plan, new BigDecimal(total), new BigDecimal(total));
    }

    /** The INSERT a pre-integration (V5) image issues: no price breakdown columns. */
    private static void insertOldBooking(Connection c, String ref, long hall, long seat, String day, String total)
            throws SQLException {
        insert(c, "INSERT INTO bookings (booking_reference, user_id, study_hall_id, seat_id, start_date, end_date,"
                        + " status, plan, unit_price, total_price) VALUES (?, 7, ?, ?, ?::date, ?::date, 'CANCELLED', 'DAILY', ?, ?)",
                ref, hall, seat, day, day, new BigDecimal(total), new BigDecimal(total));
    }

    private static void insertNewBooking(Connection c, String ref, long hall, long seat, String total, String base,
                                         String discount, String fees, String tax) throws SQLException {
        insert(c, "INSERT INTO bookings (booking_reference, user_id, study_hall_id, seat_id, start_date, end_date, status,"
                        + " plan, unit_price, total_price, base_amount, discount_amount, platform_charges, tax_amount)"
                        + " VALUES (?, 7, ?, ?, DATE '2027-03-01', DATE '2027-03-01', 'CANCELLED', 'DAILY', ?, ?, ?, ?, ?, ?)",
                ref, hall, seat, new BigDecimal(base), new BigDecimal(total), new BigDecimal(base),
                new BigDecimal(discount), new BigDecimal(fees), new BigDecimal(tax));
    }

    // ---------------------------------------------------------------- helpers

    private static void snapshot(Connection c) throws SQLException {
        VENDORS_BEFORE.putAll(vendorRows(c));
    }

    private static Map<String, Map<String, Object>> vendorRows() throws SQLException {
        try (Connection c = connect()) {
            return vendorRows(c);
        }
    }

    private static Map<String, Map<String, Object>> vendorRows(Connection c) throws SQLException {
        Map<String, Map<String, Object>> rows = new TreeMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT business_name, status, submitted_at, updated_at, version FROM vendor_profiles")) {
            while (rs.next()) {
                Map<String, Object> row = new TreeMap<>();
                row.put("status", rs.getString("status"));
                row.put("submitted_at", rs.getTimestamp("submitted_at"));
                row.put("updated_at", rs.getTimestamp("updated_at"));
                row.put("version", rs.getLong("version"));
                rows.put(rs.getString("business_name"), row);
            }
        }
        return rows;
    }

    private static List<BigDecimal> totals() throws SQLException {
        List<BigDecimal> totals = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT total_price FROM bookings WHERE booking_reference LIKE 'LEGACY-%' ORDER BY id")) {
            while (rs.next()) {
                totals.add(rs.getBigDecimal(1));
            }
        }
        return totals;
    }

    private static long insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong("id") : -1;
            }
        }
    }

    private static long scalarLong(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String scalarString(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(String location) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(location);
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
