package com.spacz.admin.controller;

import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.dto.AdminVendorDetailResponse;
import com.spacz.admin.dto.OptionalReasonRequest;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.dto.ReasonRequest;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.VendorAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code vendorId} is the vendor's auth account ID (the same ID used everywhere on the platform).
 */
@RestController
@RequestMapping("/api/admin/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Vendors", description = "Vendor / institute approval workflow")
public class AdminVendorController {

    private final VendorAdminService vendorAdminService;

    @GetMapping
    @Operation(summary = "Search vendors", description = "e.g. ?status=PENDING for the approval queue")
    public PageResponse<AdminVendorDto> search(@Parameter(description = "Business/contact name, email or phone contains")
                                               @RequestParam(required = false) String search,
                                               @Parameter(description = "DRAFT, PENDING, APPROVED, REJECTED, SUSPENDED, ACTIVE, INACTIVE")
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String city,
                                               @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return vendorAdminService.search(search, status, city, pageable);
    }

    @GetMapping("/{vendorId}")
    @Operation(summary = "Vendor profile with study-hall counts and login account")
    public AdminVendorDetailResponse get(@PathVariable Long vendorId) {
        return vendorAdminService.get(vendorId);
    }

    @PostMapping("/{vendorId}/approve")
    @Operation(summary = "Approve (PENDING or REJECTED → APPROVED); its approved halls go live")
    public AdminVendorDto approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long vendorId,
                                  @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return vendorAdminService.approve(admin, vendorId, request == null ? null : request.reason());
    }

    @PostMapping("/{vendorId}/reject")
    @Operation(summary = "Reject (PENDING → REJECTED); reason required")
    public AdminVendorDto reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long vendorId,
                                 @Valid @RequestBody ReasonRequest request) {
        return vendorAdminService.reject(admin, vendorId, request.reason());
    }

    @PostMapping("/{vendorId}/suspend")
    @Operation(summary = "Suspend (hides the vendor's halls, blocks changes); reason required")
    public AdminVendorDto suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long vendorId,
                                  @Valid @RequestBody ReasonRequest request) {
        return vendorAdminService.suspend(admin, vendorId, request.reason());
    }

    @PostMapping("/{vendorId}/activate")
    @Operation(summary = "Activate (SUSPENDED/INACTIVE → ACTIVE)")
    public AdminVendorDto activate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long vendorId,
                                   @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return vendorAdminService.activate(admin, vendorId, request == null ? null : request.reason());
    }
}
