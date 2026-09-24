package com.spacz.studyhall.controller.legacy;

import com.spacz.studyhall.dto.legacy.LegacyRequests;
import com.spacz.studyhall.dto.legacy.LegacyResponses;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.LegacyPartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@LegacyApi
public class LegacyPropertyController {

    private final LegacyPartnerService service;

    @PostMapping
    public LegacyResponses.PropertyResponse create(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                   @Valid @RequestBody LegacyRequests.PropertyRequest request) {
        return service.createProperty(vendor.userId(), request);
    }

    @GetMapping
    public List<LegacyResponses.PropertyResponse> list(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return service.properties(vendor.userId());
    }

    @GetMapping("/{id}")
    public LegacyResponses.PropertyResponse get(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.property(vendor.userId(), id);
    }

    @PutMapping("/{id}")
    public LegacyResponses.PropertyResponse update(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                   @Valid @RequestBody LegacyRequests.PropertyRequest request) {
        return service.updateProperty(vendor.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        service.deleteProperty(vendor.userId(), id);
    }

    @GetMapping("/{id}/blocks")
    public List<LegacyResponses.BlockResponse> blocks(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.propertyBlocks(vendor.userId(), id);
    }

    @GetMapping("/{id}/images")
    public List<LegacyResponses.ImageResponse> images(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.propertyImages(vendor.userId(), id);
    }
}
