package com.spacz.studyhall.audit;

import com.spacz.studyhall.client.AdminServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Best effort: an admin-service outage never fails the business operation; the event is logged instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventForwarder {

    private final AdminServiceClient adminServiceClient;

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void forward(AuditEvent event) {
        try {
            adminServiceClient.recordAuditEvent(event);
        } catch (RuntimeException ex) {
            log.warn("Audit event {} for {} {} not delivered: {}", event.action(), event.entityType(), event.entityId(),
                    ex.getMessage());
        }
    }
}
