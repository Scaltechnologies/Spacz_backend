package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.AmenityIdsRequest;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.hall.HallVisibilityRequest;
import com.spacz.studyhall.dto.hall.ImageRequest;
import com.spacz.studyhall.dto.hall.OperatingHoursRequest;
import com.spacz.studyhall.dto.hall.ProgramIdsRequest;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSearchCriteria;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.hall.UpdateStudyHallRequest;
import com.spacz.studyhall.dto.seat.AvailabilityResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.StudyHallQueryService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/studyhalls")
@RequiredArgsConstructor
@Tag(name = "Study halls", description = "Public search and details (no token needed); management by the owning vendor")
@ApiErrorResponses
public class StudyHallController {

    private final StudyHallQueryService queryService;
    private final StudyHallManagementService managementService;

    @GetMapping
    @Operation(summary = "Search live study halls",
            description = "All filters optional (AND). Sortable by name, city, pricePerDay, pricePerMonth, createdAt. "
                    + "Example: /api/studyhalls?city=Hyderabad&programId=1&amenityIds=1,3&maxPrice=200&page=0&size=20")
    public PageResponse<StudyHallSummaryResponse> search(@ParameterObject StudyHallSearchCriteria criteria,
                                                         @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return queryService.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Study hall details", description = "Live halls for everyone; any status for the owner and admins.")
    public StudyHallDetailResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return queryService.get(id, caller);
    }

    @GetMapping("/{id}/availability")
    @Operation(summary = "Seat availability summary for a date range (defaults to today)")
    public AvailabilityResponse availability(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return queryService.availability(id, caller, startDate, endDate);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Create a study hall (DRAFT)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse create(@AuthenticationPrincipal AuthenticatedUser vendor,
                                          @Valid @RequestBody CreateStudyHallRequest request) {
        return managementService.create(vendor, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Update study hall details", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse update(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                          @Valid @RequestBody UpdateStudyHallRequest request) {
        return managementService.update(vendor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Delete a study hall that was never approved and has no bookings",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public void delete(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        managementService.delete(vendor, id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Submit for admin approval", description = "DRAFT/REJECTED → PENDING_APPROVAL. Needs a priced seat.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse submit(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return managementService.submitForApproval(vendor, id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Hide / show an approved hall", description = "ACTIVE ⇄ INACTIVE",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                @Valid @RequestBody HallVisibilityRequest request) {
        return managementService.changeVisibility(vendor.userId(), id, request.status());
    }

    @PutMapping("/{id}/operating-hours")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Replace the weekly schedule (unlisted days are closed)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse operatingHours(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                  @Valid @RequestBody OperatingHoursRequest request) {
        return managementService.replaceOperatingHours(vendor.userId(), id, request);
    }

    @PostMapping("/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Add an image by URL", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse addImage(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                            @Valid @RequestBody ImageRequest request) {
        return managementService.addImage(vendor.userId(), id, request);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Remove an image", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse removeImage(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                               @PathVariable Long imageId) {
        return managementService.removeImage(vendor.userId(), id, imageId);
    }

    @PostMapping("/{id}/amenities")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Add hall-wide amenities (IDs from GET /api/amenities)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse addAmenities(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                @Valid @RequestBody AmenityIdsRequest request) {
        return managementService.addAmenities(vendor.userId(), id, request.amenityIds());
    }

    @DeleteMapping("/{id}/amenities/{amenityId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Remove a hall-wide amenity", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse removeAmenity(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                 @PathVariable Long amenityId) {
        return managementService.removeAmenity(vendor.userId(), id, amenityId);
    }

    @PostMapping("/{id}/programs")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Add supported exams/courses (IDs from GET /api/programs)",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse addPrograms(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                               @Valid @RequestBody ProgramIdsRequest request) {
        return managementService.addPrograms(vendor.userId(), id, request.programIds());
    }

    @DeleteMapping("/{id}/programs/{programId}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Remove a supported exam/course", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse removeProgram(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                 @PathVariable Long programId) {
        return managementService.removeProgram(vendor.userId(), id, programId);
    }
}
