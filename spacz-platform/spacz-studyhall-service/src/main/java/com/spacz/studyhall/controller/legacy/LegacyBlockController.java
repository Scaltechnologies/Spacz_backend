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
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@LegacyApi
public class LegacyBlockController {

    private final LegacyPartnerService service;

    @PostMapping
    public LegacyResponses.BlockResponse create(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                @Valid @RequestBody LegacyRequests.BlockRequest request) {
        return service.createBlock(vendor.userId(), request);
    }

    @GetMapping
    public List<LegacyResponses.BlockResponse> list(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return service.blocks(vendor.userId());
    }

    @GetMapping("/{id}")
    public LegacyResponses.BlockResponse get(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.block(vendor.userId(), id);
    }

    @PutMapping("/{id}")
    public LegacyResponses.BlockResponse update(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                @Valid @RequestBody LegacyRequests.BlockRequest request) {
        return service.updateBlock(vendor.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        service.deleteBlock(vendor.userId(), id);
    }

    @GetMapping("/{id}/seats")
    public List<LegacyResponses.SeatResponse> seats(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.blockSeats(vendor.userId(), id);
    }

    @GetMapping("/{id}/amenity")
    public LegacyResponses.AmenityResponse amenity(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.blockAmenity(vendor.userId(), id);
    }
}
