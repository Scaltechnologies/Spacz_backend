package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.AmenityIdsRequest;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.hall.HallVisibilityRequest;
import com.spacz.studyhall.dto.hall.ImageRequest;
import com.spacz.studyhall.dto.hall.OperatingHoursRequest;
import com.spacz.studyhall.dto.hall.ProgramIdsRequest;
import com.spacz.studyhall.dto.hall.LocationResponse;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSearchCriteria;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.hall.UpdateStudyHallRequest;
import com.spacz.studyhall.dto.seat.AvailabilityResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.StudyHallQueryService;
import com.spacz.studyhall.service.impl.HallImageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.List;

@RestController
@RequestMapping("/api/studyhalls")
@RequiredArgsConstructor
@Tag(name = "Study halls", description = "Public search and details (no token needed); management by the owning vendor")
@ApiErrorResponses
public class StudyHallController {

    private final HallImageService hallImageService;
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

    @GetMapping("/locations")
    @Operation(summary = "Cities with live study halls", description = "For the student app's location filter; most halls first.")
    public List<LocationResponse> locations() {
        return queryService.locations();
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

    @PostMapping(value = "/{id}/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Upload a photo (JPEG/PNG, multipart part `file`)",
            description = "Stored by the service and served from GET /api/studyhalls/media/{hallId}/{file}. "
                    + "The first photo becomes the cover unless another is chosen.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public StudyHallDetailResponse uploadImage(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String caption,
                                               @RequestParam(defaultValue = "false") boolean cover) {
        return hallImageService.upload(vendor.userId(), id, file, caption, cover);
    }

    @GetMapping("/media/{hallId}/{file:.+}")
    @Operation(summary = "An uploaded study hall photo (public)")
    public ResponseEntity<InputStreamResource> media(@PathVariable Long hallId, @PathVariable String file) {
        HallImageService.StoredImage image = hallImageService.open(hallId, file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.sizeBytes())
                // file names are random and never reused, so the content never changes
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic().immutable())
                .body(new InputStreamResource(image.content()));
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
