package com.spacz.admin.controller;

import com.spacz.admin.config.OpenApiConfig;
import com.spacz.admin.dto.ActiveRequest;
import com.spacz.admin.dto.OfferRequest;
import com.spacz.admin.dto.OfferResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.exception.ApiError;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/offers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "Admin - Offers", description = "Offers shown to students and applied to booking prices")
@ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Not an admin", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class AdminOfferController {

    private final OfferService offerService;

    @GetMapping
    @Operation(summary = "All non-archived offers (live or not), highest priority first")
    public PageResponse<OfferResponse> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return offerService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "An offer")
    @ApiResponse(responseCode = "404", description = "Not found or archived", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OfferResponse get(@PathVariable Long id) {
        return offerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an offer")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OfferResponse create(@AuthenticationPrincipal AuthenticatedUser admin, @Valid @RequestBody OfferRequest request) {
        return offerService.create(admin, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an offer")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public OfferResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                @Valid @RequestBody OfferRequest request) {
        return offerService.update(admin, id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable / disable an offer")
    public OfferResponse setActive(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                   @Valid @RequestBody ActiveRequest request) {
        return offerService.setActive(admin, id, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive an offer (soft delete; it disappears everywhere)")
    public void archive(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        offerService.archive(admin, id);
    }
}
