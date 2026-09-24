package com.spacz.user.dto;

import com.spacz.user.entity.PreparationStatus;

import java.time.Instant;
import java.time.LocalDate;

public record UserProgramResponse(Long id, Long userId, Long programId, String programCode, String programName,
                                  LocalDate startDate, LocalDate targetDate, PreparationStatus status, String notes,
                                  Instant createdAt, Instant updatedAt) {
}
