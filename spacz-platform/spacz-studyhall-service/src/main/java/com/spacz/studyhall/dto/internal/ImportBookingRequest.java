package com.spacz.studyhall.dto.internal;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ImportBookingRequest(@NotNull Long legacyBookingId, @NotNull Long userId, @NotNull Long legacySeatId,
                                   @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
}
