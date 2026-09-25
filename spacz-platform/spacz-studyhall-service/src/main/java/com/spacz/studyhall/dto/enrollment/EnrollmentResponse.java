package com.spacz.studyhall.dto.enrollment;

import com.spacz.studyhall.dto.hall.ProgramRef;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.EnrollmentSource;
import com.spacz.studyhall.entity.EnrollmentStatus;

import java.time.Instant;
import java.time.LocalDate;

public record EnrollmentResponse(Long id, Long studyHallId, String studyHallName, StudentInfo student, ProgramRef program,
                                 Long seatId, String seatNumber, BookingPlan plan, LocalDate startDate,
                                 LocalDate endDate, EnrollmentStatus status, EnrollmentSource source, String notes,
                                 Instant createdAt, Instant updatedAt) {
}
