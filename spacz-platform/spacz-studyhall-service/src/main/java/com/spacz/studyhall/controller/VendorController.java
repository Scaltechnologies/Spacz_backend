package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.VendorBookingResponse;
import com.spacz.studyhall.dto.enrollment.EnrollmentResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileResponse;
import com.spacz.studyhall.dto.vendor.VendorPublicResponse;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.EnrollmentStatus;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendors / institutes", description = "Vendor profile, approval submission, own study halls, students and bookings")
@ApiErrorResponses
public class VendorController {

    private final VendorService vendorService;
    private final StudyHallManagementService hallService;
    private final EnrollmentService enrollmentService;
    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Create my vendor profile (DRAFT)",
            description = "Registration usually creates it already; use this when GET /me returns 404.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public VendorProfileResponse create(@AuthenticationPrincipal AuthenticatedUser vendor,
                                        @Valid @RequestBody VendorProfileRequest request) {
        return vendorService.create(vendor, request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "My vendor profile, approval status and missing fields",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public VendorProfileResponse me(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return vendorService.getMine(vendor.userId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Update my vendor profile", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public VendorProfileResponse update(@AuthenticationPrincipal AuthenticatedUser vendor,
                                        @Valid @RequestBody VendorProfileRequest request) {
        return vendorService.updateMine(vendor, request);
    }

    @PostMapping("/me/submit")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Submit my profile for admin approval", description = "DRAFT/REJECTED → PENDING; needs a complete profile.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public VendorProfileResponse submit(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return vendorService.submit(vendor);
    }

    @GetMapping("/me/studyhalls")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "My study halls (any status)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public PageResponse<StudyHallSummaryResponse> myHalls(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                          @RequestParam(required = false) StudyHallStatus status,
                                                          @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return hallService.listMine(vendor.userId(), status, pageable);
    }

    @GetMapping("/me/students")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "My students (enrollments) across halls",
            description = "`search` matches walk-in names/phones/emails.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public PageResponse<EnrollmentResponse> students(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                     @RequestParam(required = false) Long studyHallId,
                                                     @RequestParam(required = false) EnrollmentStatus status,
                                                     @RequestParam(required = false) Long programId,
                                                     @RequestParam(required = false) String search,
                                                     @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return enrollmentService.students(vendor.userId(), studyHallId, status, programId, search, pageable);
    }

    @GetMapping("/me/bookings")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Bookings across my study halls", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public PageResponse<VendorBookingResponse> bookings(
            @AuthenticationPrincipal AuthenticatedUser vendor,
            @RequestParam(required = false) Long studyHallId,
            @RequestParam(required = false) BookingStatus status,
            @Parameter(description = "Bookings overlapping this date range")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.vendorBookings(vendor.userId(), studyHallId, status, from, to, pageable);
    }

    @GetMapping("/{vendorId}")
    @Operation(summary = "Public profile of an approved vendor / institute")
    public VendorPublicResponse publicProfile(@PathVariable Long vendorId) {
        return vendorService.getPublic(vendorId);
    }
}
