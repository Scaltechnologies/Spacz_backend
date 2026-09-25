package com.spacz.auth.audit;

import com.spacz.auth.config.CorrelationIdFilter;
import com.spacz.auth.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records audit events. The event is captured now (actor, IP, correlation ID) and delivered to
 * admin-service only after the surrounding transaction commits, so rolled-back work is never audited.
 */
@Component
@RequiredArgsConstructor
public class AuditEvents {

    private final ApplicationEventPublisher publisher;

    public void record(AuthenticatedUser actor, String action, String entityType, Long entityId, String description) {
        record(actor == null ? null : actor.userId(), actor == null ? "SYSTEM" : actor.role().name(),
                action, entityType, entityId, description);
    }

    public void record(Long actorUserId, String actorRole, String action, String entityType, Long entityId,
                       String description) {
        String ip = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr() : null;
        publisher.publishEvent(new AuditEvent(actorUserId, actorRole, action, entityType, entityId,
                description, ip, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
