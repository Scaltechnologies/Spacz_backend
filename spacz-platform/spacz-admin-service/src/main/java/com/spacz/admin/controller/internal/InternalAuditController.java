package com.spacz.admin.controller.internal;

import com.spacz.admin.dto.AuditEventRequest;
import com.spacz.admin.dto.AuditLogResponse;
import com.spacz.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit-events")
@RequiredArgsConstructor
@Tag(name = "Internal - Audit events", description = "Domain events reported by auth-service and studyhall-service")
@SecurityRequirement(name = "internalApiKey")
public class InternalAuditController {

    private final AuditService auditService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append a domain event to the audit log")
    public AuditLogResponse record(@Valid @RequestBody AuditEventRequest request) {
        return auditService.recordEvent(request);
    }
}
