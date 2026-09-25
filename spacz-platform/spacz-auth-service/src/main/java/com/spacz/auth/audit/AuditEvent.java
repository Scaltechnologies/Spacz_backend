package com.spacz.auth.audit;

/**
 * A domain event for the platform audit log, owned by admin-service.
 *
 * @param actorUserId null for system events
 * @param actorRole   ADMIN, USER, VENDOR or SYSTEM
 * @param action      an AuditAction name known to admin-service (e.g. VENDOR_REGISTERED)
 */
public record AuditEvent(Long actorUserId, String actorRole, String action, String entityType, Long entityId,
                         String description, String ipAddress, String correlationId) {
}
