package com.spacz.admin.dto;

import com.spacz.admin.entity.AuditAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A domain event reported by another service (auth-service, studyhall-service).
 */
public record AuditEventRequest(Long actorUserId,
                                @NotBlank @Size(max = 20) String actorRole,
                                @NotNull AuditAction action,
                                @NotBlank @Size(max = 40) String entityType,
                                Long entityId,
                                @Size(max = 1000) String description,
                                @Size(max = 64) String ipAddress,
                                @Size(max = 64) String correlationId) {
}
