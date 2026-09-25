package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.VendorBookingResponse;
import com.spacz.studyhall.dto.enrollment.CreateEnrollmentRequest;
import com.spacz.studyhall.dto.enrollment.EnrollmentResponse;
import com.spacz.studyhall.dto.enrollment.UpdateEnrollmentRequest;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.EnrollmentStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/studyhalls/{studyHallId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@Tag(name = "Hall students & bookings", description = "The vendor's students (enrollments) and bookings of one hall")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final BookingService bookingService;

    @GetMapping("/enrollments")
    @Operation(summary = "Students of this hall")
    public PageResponse<EnrollmentResponse> list(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                 @PathVariable Long studyHallId,
                                                 @RequestParam(required = false) EnrollmentStatus status,
                                                 @RequestParam(required = false) Long programId,
                                                 @RequestParam(required = false) String search,
                                                 @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return enrollmentService.students(vendor.userId(), studyHallId, status, programId, search, pageable);
    }

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a student (SPACZ account or walk-in); an optional seat is held for them")
    public EnrollmentResponse create(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                                     @Valid @RequestBody CreateEnrollmentRequest request) {
        return enrollmentService.create(vendor.userId(), studyHallId, request);
    }

    @PatchMapping("/enrollments/{enrollmentId}")
    @Operation(summary = "Change seat / exam / end date, or end the enrollment")
    public EnrollmentResponse update(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long studyHallId,
                                     @PathVariable Long enrollmentId, @Valid @RequestBody UpdateEnrollmentRequest request) {
        return enrollmentService.update(vendor.userId(), studyHallId, enrollmentId, request);
    }

    @GetMapping("/bookings")
    @Operation(summary = "Bookings of this hall, with student contact details")
    public PageResponse<VendorBookingResponse> bookings(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                        @PathVariable Long studyHallId,
                                                        @RequestParam(required = false) BookingStatus status,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.vendorBookings(vendor.userId(), studyHallId, status, from, to, pageable);
    }
}
