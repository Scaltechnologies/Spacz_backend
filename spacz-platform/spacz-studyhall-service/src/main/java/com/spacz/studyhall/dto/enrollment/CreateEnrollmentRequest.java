package com.spacz.studyhall.dto.enrollment;

import com.spacz.studyhall.dto.ValidationPatterns;
import com.spacz.studyhall.entity.BookingPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = """
        Adds a student to the hall. Either `userId` (an existing SPACZ student) or `guestName` (walk-in) is required.
        A `seatId` is held for the student (status RESERVED) until the enrollment ends.""")
public record CreateEnrollmentRequest(
        @Schema(description = "SPACZ account ID of the student") Long userId,
        @Schema(example = "Kiran Rao") @Size(max = 120) String guestName,
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String guestPhone,
        @Email @Size(max = 254) String guestEmail,
        Long programId,
        Long seatId,
        @Schema(description = "Defaults to MONTHLY") BookingPlan plan,
        @Schema(example = "2026-10-01") @NotNull LocalDate startDate,
        @Schema(example = "2026-12-31", description = "Open-ended when omitted") LocalDate endDate,
        @Size(max = 500) String notes) {

    @AssertTrue(message = "either userId or guestName is required (not both)")
    @Schema(hidden = true)
    public boolean isStudentValid() {
        boolean hasGuest = guestName != null && !guestName.isBlank();
        return (userId != null) != hasGuest;
    }

    @AssertTrue(message = "endDate must not be before startDate")
    @Schema(hidden = true)
    public boolean isRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
