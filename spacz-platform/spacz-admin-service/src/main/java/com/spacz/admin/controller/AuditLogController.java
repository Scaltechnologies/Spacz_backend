package com.spacz.admin.controller;

import com.spacz.admin.dto.AuditLogResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Audit logs", description = "Admin actions and platform events (registrations, hall changes ...)")
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Search audit logs, newest first")
    public PageResponse<AuditLogResponse> search(
            @Parameter(description = "Description or actor email contains") @RequestParam(required = false) String search,
            @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Account ID of the actor") @RequestParam(required = false) Long actorUserId,
            @Parameter(description = "ADMIN, VENDOR, USER or SYSTEM") @RequestParam(required = false) String actorRole,
            @Parameter(description = "VENDOR, STUDY_HALL, USER_ACCOUNT, PROGRAM, AMENITY") @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @Parameter(description = "Inclusive, ISO-8601 e.g. 2026-09-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Exclusive, ISO-8601")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(search, action, actorUserId, actorRole, entityType, entityId, from, to, pageable);
    }
}
