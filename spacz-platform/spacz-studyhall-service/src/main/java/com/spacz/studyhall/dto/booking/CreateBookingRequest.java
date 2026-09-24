package com.spacz.studyhall.dto.booking;

import com.spacz.studyhall.entity.BookingPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "DAILY: startDate..endDate (inclusive). MONTHLY: startDate + months (endDate is derived).")
public record CreateBookingRequest(
        @Schema(example = "1") @NotNull Long studyHallId,
        @Schema(example = "7") @NotNull Long seatId,
        @Schema(description = "Defaults to DAILY") BookingPlan plan,
        @Schema(example = "2026-10-01") @NotNull LocalDate startDate,
        @Schema(example = "2026-10-03", description = "Required for DAILY, ignored for MONTHLY") LocalDate endDate,
        @Schema(example = "1", description = "Required for MONTHLY") @Min(1) @Max(12) Integer months,
        @Schema(description = "Exam the student is preparing for (optional)") Long programId) {

    public BookingPlan effectivePlan() {
        return plan == null ? BookingPlan.DAILY : plan;
    }

    /** The inclusive end date: given for DAILY, derived for MONTHLY. */
    public LocalDate effectiveEndDate() {
        return effectivePlan() == BookingPlan.MONTHLY && startDate != null && months != null
                ? startDate.plusMonths(months).minusDays(1)
                : endDate;
    }

    @AssertTrue(message = "DAILY bookings need endDate (not before startDate); MONTHLY bookings need months")
    @Schema(hidden = true)
    public boolean isRangeValid() {
        if (startDate == null) {
            return true;
        }
        if (effectivePlan() == BookingPlan.MONTHLY) {
            return months != null;
        }
        return endDate != null && !endDate.isBefore(startDate);
    }
}
