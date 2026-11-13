package com.spacz.admin.controller;

import com.spacz.admin.config.OpenApiConfig;
import com.spacz.admin.dto.ActiveRequest;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.dto.PlatformUpdateRequest;
import com.spacz.admin.dto.PlatformUpdateResponse;
import com.spacz.admin.exception.ApiError;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.PlatformUpdateService;
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
@RequestMapping("/api/admin/updates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@Tag(name = "Admin - Updates", description = "Announcements shown in the student app's Notifications → Updates tab")
@ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Not an admin", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class AdminPlatformUpdateController {

    private final PlatformUpdateService updateService;

    @GetMapping
    @Operation(summary = "All non-archived updates (published or draft), newest first")
    public PageResponse<PlatformUpdateResponse> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return updateService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "An update")
    @ApiResponse(responseCode = "404", description = "Not found or archived", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PlatformUpdateResponse get(@PathVariable Long id) {
        return updateService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an update (draft unless published=true)")
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PlatformUpdateResponse create(@AuthenticationPrincipal AuthenticatedUser admin,
                                         @Valid @RequestBody PlatformUpdateRequest request) {
        return updateService.create(admin, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace an update's title/description (published=null keeps the publish state)")
    public PlatformUpdateResponse update(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                         @Valid @RequestBody PlatformUpdateRequest request) {
        return updateService.update(admin, id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publish (active=true) / unpublish (active=false)")
    public PlatformUpdateResponse setPublished(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                               @Valid @RequestBody ActiveRequest request) {
        return updateService.setPublished(admin, id, request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive an update (soft delete)")
    public void archive(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        updateService.archive(admin, id);
    }
}
