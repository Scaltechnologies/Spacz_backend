package com.spacz.admin.controller;

import com.spacz.admin.client.dto.StudyHallDetailDto;
import com.spacz.admin.client.dto.StudyHallSummaryDto;
import com.spacz.admin.dto.OptionalReasonRequest;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.dto.ReasonRequest;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.StudyHallModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
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

@RestController
@RequestMapping("/api/admin/studyhalls")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Study halls", description = "Study-hall approval and moderation")
public class AdminStudyHallController {

    private final StudyHallModerationService moderationService;

    @GetMapping
    @Operation(summary = "Search study halls in any status", description = "e.g. ?status=PENDING_APPROVAL for the review queue")
    public PageResponse<StudyHallSummaryDto> search(@RequestParam(required = false) String search,
                                                    @Parameter(description = "DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, SUSPENDED, ACTIVE, INACTIVE")
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String city,
                                                    @RequestParam(required = false) Long vendorId,
                                                    @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return moderationService.search(search, status, city, vendorId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Study hall details (any status)")
    public StudyHallDetailDto get(@PathVariable Long id) {
        return moderationService.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve (PENDING_APPROVAL → ACTIVE, or APPROVED until the vendor is approved)")
    public StudyHallDetailDto approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                      @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return moderationService.approve(admin, id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject (PENDING_APPROVAL → REJECTED); reason required")
    public StudyHallDetailDto reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                     @Valid @RequestBody ReasonRequest request) {
        return moderationService.reject(admin, id, request.reason());
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend (hidden from search, no new bookings); reason required")
    public StudyHallDetailDto suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                      @Valid @RequestBody ReasonRequest request) {
        return moderationService.suspend(admin, id, request.reason());
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Re-activate a suspended study hall")
    public StudyHallDetailDto activate(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                                       @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return moderationService.activate(admin, id, request == null ? null : request.reason());
    }
}
