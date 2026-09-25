package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.enrollment.CreateEnrollmentRequest;
import com.spacz.studyhall.dto.enrollment.EnrollmentResponse;
import com.spacz.studyhall.dto.enrollment.UpdateEnrollmentRequest;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.EnrollmentStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Hall memberships: the vendor's student list and the student's "my study halls".
 */
public interface EnrollmentService {

    /** The vendor's students, across all halls or one hall. */
    PageResponse<EnrollmentResponse> students(Long vendorId, Long studyHallId, EnrollmentStatus status, Long programId,
                                              String search, Pageable pageable);

    EnrollmentResponse create(Long vendorId, Long studyHallId, CreateEnrollmentRequest request);

    EnrollmentResponse update(Long vendorId, Long studyHallId, Long enrollmentId, UpdateEnrollmentRequest request);

    /** A student's memberships (for user-service). */
    List<EnrollmentResponse> forUser(Long userId);

    /** A confirmed booking makes (or extends) the student's membership of that hall. Runs in the caller's transaction. */
    void recordConfirmedBooking(Booking booking);

    /** ACTIVE memberships whose end date has passed → COMPLETED. */
    int completeEnded(int batchSize);
}
