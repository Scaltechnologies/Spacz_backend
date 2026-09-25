package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.hall.AmenityRequest;
import com.spacz.studyhall.dto.hall.AmenityResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.AmenityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/amenities")
@RequiredArgsConstructor
@Tag(name = "Amenities", description = "Catalog: public reads, ADMIN writes")
@ApiErrorResponses
public class AmenityController {

    private final AmenityService amenityService;

    @GetMapping
    @Operation(summary = "Active amenities (for search filters and hall setup)")
    public List<AmenityResponse> list() {
        return amenityService.listActive();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "All amenities incl. inactive", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public List<AmenityResponse> all() {
        return amenityService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an amenity", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public AmenityResponse create(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody AmenityRequest request) {
        return amenityService.create(admin, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an amenity (active=false retires it)", security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    public AmenityResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                  @Valid @RequestBody AmenityRequest request) {
        return amenityService.update(admin, id, request);
    }
}
