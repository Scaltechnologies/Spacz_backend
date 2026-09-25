package com.spacz.studyhall.dto.hall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Replaces the weekly schedule. Days not listed are marked closed.")
public record OperatingHoursRequest(@NotEmpty @Size(max = 7) List<@Valid @NotNull DayHours> days) {

    @AssertTrue(message = "each day may appear only once")
    @Schema(hidden = true)
    public boolean isDaysUnique() {
        return days == null || days.stream().filter(d -> d != null && d.dayOfWeek() != null)
                .map(DayHours::dayOfWeek).distinct().count()
                == days.stream().filter(d -> d != null && d.dayOfWeek() != null).count();
    }

    public record DayHours(
            @Schema(example = "MONDAY") @NotNull DayOfWeek dayOfWeek,
            @Schema(example = "06:00") LocalTime openTime,
            @Schema(example = "22:00") LocalTime closeTime,
            boolean closed) {

        @AssertTrue(message = "an open day needs openTime before closeTime")
        @Schema(hidden = true)
        public boolean isTimesValid() {
            return closed || (openTime != null && closeTime != null && closeTime.isAfter(openTime));
        }
    }
}
