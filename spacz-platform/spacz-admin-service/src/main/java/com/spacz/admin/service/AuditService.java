package com.spacz.admin.service;

import com.spacz.admin.dto.AuditEventRequest;
import com.spacz.admin.dto.AuditLogResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface AuditService {

    /** Records an action by the current admin; IP, user agent and correlation ID come from the current request. */
    AuditLogResponse record(AuthenticatedUser actor, AuditAction action, String entityType, Long entityId,
                            String description);

    /** Records a domain event reported by another service. */
    AuditLogResponse recordEvent(AuditEventRequest event);

    PageResponse<AuditLogResponse> search(String search, AuditAction action, Long actorUserId, String actorRole,
                                          String entityType, Long entityId, Instant from, Instant to, Pageable pageable);
}
