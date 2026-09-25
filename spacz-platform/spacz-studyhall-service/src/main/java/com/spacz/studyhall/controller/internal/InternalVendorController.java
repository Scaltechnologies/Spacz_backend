package com.spacz.studyhall.controller.internal;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.StatusActionRequest;
import com.spacz.studyhall.dto.vendor.AdminVendorResponse;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileResponse;
import com.spacz.studyhall.entity.VendorStatus;
import com.spacz.studyhall.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/vendors")
@RequiredArgsConstructor
@Tag(name = "Internal - Vendors", description = "Used by auth-service (registration) and admin-service")
@SecurityRequirement(name = OpenApiConfig.INTERNAL_KEY)
public class InternalVendorController {

    private final VendorService vendorService;

    @PostMapping
    @Operation(summary = "Create a vendor profile at registration (idempotent on vendorId)")
    public ResponseEntity<VendorProfileResponse> create(@Valid @RequestBody CreateVendorProfileRequest request) {
        VendorService.CreationResult result = vendorService.createFromRegistration(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.profile());
    }

    @GetMapping
    @Operation(summary = "Search vendors")
    public PageResponse<AdminVendorResponse> search(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) VendorStatus status,
                                                    @RequestParam(required = false) String city,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "createdAt",
                                                            direction = Sort.Direction.DESC) Pageable pageable) {
        return vendorService.search(search, status, city, pageable);
    }

    @GetMapping("/{vendorId}")
    @Operation(summary = "One vendor by vendorId (= auth account ID)")
    public AdminVendorResponse get(@PathVariable Long vendorId) {
        return vendorService.getForAdmin(vendorId);
    }

    @PatchMapping("/{vendorId}/status")
    @Operation(summary = "APPROVE / REJECT / SUSPEND / ACTIVATE a vendor")
    public AdminVendorResponse changeStatus(@PathVariable Long vendorId, @Valid @RequestBody StatusActionRequest request) {
        return vendorService.applyAction(vendorId, request.action(), request.reason());
    }
}
