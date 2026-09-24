package com.spacz.user.dto;

import com.spacz.user.entity.PreparationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserProgramRequest(
        @Schema(example = "1", description = "ID from GET /api/programs") @NotNull Long programId,
        @Schema(example = "2026-10-01") LocalDate startDate,
        @Schema(example = "2027-05-25") LocalDate targetDate,
        @Schema(description = "Defaults to PLANNED") PreparationStatus status,
        @Size(max = 500) String notes) {

    @AssertTrue(message = "targetDate must not be before startDate")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return startDate == null || targetDate == null || !targetDate.isBefore(startDate);
    }
}
