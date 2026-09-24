package com.spacz.studyhall.controller.internal;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.StatsResponse;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.enrollment.EnrollmentResponse;
import com.spacz.studyhall.dto.internal.ImportBookingRequest;
import com.spacz.studyhall.dto.internal.ImportBookingResult;
import com.spacz.studyhall.dto.internal.ImportVendorRequest;
import com.spacz.studyhall.dto.internal.ImportVendorResult;
import com.spacz.studyhall.dto.program.ProgramResponse;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.LegacyImportService;
import com.spacz.studyhall.service.ProgramService;
import com.spacz.studyhall.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Tag(name = "Internal - Platform", description = "Bookings, enrollments, program lookup, statistics, legacy import")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_KEY)
public class InternalPlatformController {

    private final BookingService bookingService;
    private final EnrollmentService enrollmentService;
    private final ProgramService programService;
    private final StatsService statsService;
    private final LegacyImportService importService;

    @GetMapping("/bookings")
    @Operation(summary = "Search all bookings (admin-service)")
    public PageResponse<BookingResponse> bookings(@RequestParam(required = false) Long studyHallId,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) BookingStatus status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.search(studyHallId, userId, status, from, to, pageable);
    }

    @GetMapping("/enrollments")
    @Operation(summary = "A student's hall memberships (user-service, admin-service)")
    public List<EnrollmentResponse> enrollments(@RequestParam Long userId) {
        return enrollmentService.forUser(userId);
    }

    @GetMapping("/programs/lookup")
    @Operation(summary = "Batch lookup of programs by ID (max 100); unknown IDs are omitted (user-service)")
    public List<ProgramResponse> lookupPrograms(@RequestParam @Size(max = 100) Set<Long> ids) {
        return programService.lookup(ids);
    }

    @GetMapping("/stats")
    @Operation(summary = "Vendor, study hall, booking and enrollment statistics (admin dashboard)")
    public StatsResponse stats() {
        return statsService.stats();
    }

    @PostMapping("/import/vendors")
    @Operation(summary = "Import one legacy owner with its listings (migration tool; idempotent on legacyOwnerId)")
    public ImportVendorResult importVendor(@Valid @RequestBody ImportVendorRequest request) {
        return importService.importVendor(request);
    }

    @PostMapping("/import/bookings")
    @Operation(summary = "Import one legacy booking (migration tool; idempotent on legacyBookingId)")
    public ImportBookingResult importBooking(@Valid @RequestBody ImportBookingRequest request) {
        return importService.importBooking(request);
    }
}
