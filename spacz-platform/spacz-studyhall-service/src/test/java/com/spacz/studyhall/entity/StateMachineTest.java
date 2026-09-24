package com.spacz.studyhall.entity;

import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateMachineTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Test
    void vendorMustCompleteProfileBeforeSubmitting() {
        VendorProfile vendor = VendorProfile.draft(1L);
        vendor.setBusinessName("Focus");
        assertThatThrownBy(() -> vendor.submit(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("contactName").hasMessageContaining("city");
        assertThatThrownBy(() -> vendor.apply(StatusAction.APPROVE, null, NOW))
                .extracting("errorCode").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void rejectAndSuspendNeedAReason() {
        VendorProfile vendor = Fixtures.approvedVendor(2L);
        assertThatThrownBy(() -> vendor.apply(StatusAction.SUSPEND, " ", NOW))
                .extracting("errorCode").isEqualTo("REASON_REQUIRED");
        vendor.apply(StatusAction.SUSPEND, "Complaints", NOW);
        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.SUSPENDED);
        vendor.apply(StatusAction.ACTIVATE, null, NOW);
        assertThat(vendor.isOperational()).isTrue();
    }

    @Test
    void hallApprovedBeforeItsVendorGoesLiveWhenTheVendorIsApproved() {
        VendorProfile vendor = VendorProfile.draft(3L);
        vendor.setBusinessName("B");
        vendor.setContactName("C");
        vendor.setPhone("+919999999999");
        vendor.setAddressLine("A");
        vendor.setCity("Pune");
        vendor.submit(NOW);
        StudyHall hall = StudyHall.draft(vendor);
        hall.submitForApproval(true, NOW);
        hall.apply(StatusAction.APPROVE, null, NOW);
        assertThat(hall.getStatus()).isEqualTo(StudyHallStatus.APPROVED);
        assertThat(hall.isBookable()).isFalse();

        vendor.apply(StatusAction.APPROVE, null, NOW);
        hall.activateIfApproved();
        assertThat(hall.getStatus()).isEqualTo(StudyHallStatus.ACTIVE);
        assertThat(hall.isBookable()).isTrue();
    }

    @Test
    void pricesInheritSeatThenBlockThenHall() {
        StudyHall hall = Fixtures.activeHall(10L, Fixtures.approvedVendor(4L), "100.00", null);
        Block block = Fixtures.block(20L, hall);
        Seat seat = Fixtures.seat(30L, block, "A1");
        assertThat(seat.effectiveDailyPrice()).isEqualByComparingTo("100.00");
        assertThat(seat.effectiveMonthlyPrice()).isNull();

        block.setDailyPrice(new BigDecimal("150.00"));
        block.setMonthlyPrice(new BigDecimal("2500.00"));
        assertThat(seat.effectivePrice(BookingPlan.DAILY)).isEqualByComparingTo("150.00");
        assertThat(seat.effectivePrice(BookingPlan.MONTHLY)).isEqualByComparingTo("2500.00");

        seat.setPricePerDay(new BigDecimal("200.00"));
        assertThat(seat.effectiveDailyPrice()).isEqualByComparingTo("200.00");
    }

    @Test
    void monthlyBookingUnitsAreWholeMonths() {
        assertThat(Booking.units(BookingPlan.MONTHLY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31))).isEqualTo(3);
        assertThat(Booking.units(BookingPlan.DAILY, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))).isEqualTo(3);
    }

    @Test
    void legacySeatsFillTheGridRowByRowAndGrowIt() {
        StudyHall hall = Fixtures.activeHall(11L, Fixtures.approvedVendor(5L), "100.00", null);
        Block block = Block.of(hall, "Legacy", 1, 2, 1);
        for (int i = 0; i < 3; i++) {
            int[] cell = block.nextFreeCell().orElseThrow();
            block.getSeats().add(Seat.of(block, "S" + i, cell[0], cell[1], SeatType.STANDARD));
        }
        assertThat(block.getTotalRows()).isEqualTo(2);
        assertThat(block.getSeats()).extracting(Seat::getRowIndex).containsExactly(1, 1, 2);
    }
}
