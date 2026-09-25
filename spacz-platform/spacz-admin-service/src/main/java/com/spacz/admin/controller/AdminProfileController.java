package com.spacz.admin.controller;

import com.spacz.admin.dto.AdminProfileRequest;
import com.spacz.admin.dto.AdminProfileResponse;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AdminProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin profile")
public class AdminProfileController {

    private final AdminProfileService profileService;

    @GetMapping
    @Operation(summary = "My admin profile (created on first access)")
    public AdminProfileResponse me(@AuthenticationPrincipal AuthenticatedUser admin) {
        return profileService.me(admin);
    }

    @PutMapping
    @Operation(summary = "Update my admin profile")
    public AdminProfileResponse update(@AuthenticationPrincipal AuthenticatedUser admin,
                                       @Valid @RequestBody AdminProfileRequest request) {
        return profileService.update(admin, request);
    }
}
