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
@RequestMapping("/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@LegacyApi
public class LegacyImageController {

    private final LegacyPartnerService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegacyResponses.ImageResponse create(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                @Valid @RequestBody LegacyRequests.ImageRequest request) {
        return service.createImage(vendor.userId(), request);
    }

    @GetMapping
    public List<LegacyResponses.ImageResponse> list(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return service.images(vendor.userId());
    }

    @GetMapping("/{id}")
    public LegacyResponses.ImageResponse get(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return service.image(vendor.userId(), id);
    }

    @PutMapping("/{id}")
    public LegacyResponses.ImageResponse update(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id,
                                                @Valid @RequestBody LegacyRequests.ImageRequest request) {
        return service.updateImage(vendor.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        service.deleteImage(vendor.userId(), id);
    }
}
