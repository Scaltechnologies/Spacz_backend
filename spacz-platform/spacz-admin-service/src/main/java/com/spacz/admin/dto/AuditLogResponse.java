package com.spacz.admin.dto;

import com.spacz.admin.entity.AuditAction;

import java.time.Instant;

public record AuditLogResponse(Long id, Long actorUserId, String actorEmail, String actorRole, AuditAction action,
                               String entityType, Long entityId, String description, String ipAddress,
                               String userAgent, String correlationId, String sourceService, Instant timestamp) {
}
