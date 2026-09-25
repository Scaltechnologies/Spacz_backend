package com.spacz.studyhall.dto.enrollment;

import com.spacz.studyhall.entity.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Partial update: only the fields sent are changed. status COMPLETED / CANCELLED ends the "
        + "enrollment and frees its held seat.")
public record UpdateEnrollmentRequest(
        EnrollmentStatus status,
        Long seatId,
        Long programId,
        LocalDate endDate,
        @Size(max = 500) String notes) {
}
