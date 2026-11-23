package com.spacz.studyhall.payment.service.impl;

import com.spacz.studyhall.payment.entity.PaymentWebhookEvent;
import com.spacz.studyhall.payment.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes to the webhook dedupe log in their OWN transactions (REQUIRES_NEW), separate from the
 * transaction that applies the webhook's effect.
 *
 * <p>Why: catching DataIntegrityViolationException inside the processing transaction does not work
 * with Spring + Hibernate (the failed flush marks it rollback-only and the commit then throws, so a
 * duplicate delivery would get HTTP 500 and Razorpay would keep retrying). Here a duplicate insert only
 * fails this small inner transaction; the caller catches the exception outside it.
 */
@Component
@RequiredArgsConstructor
public class WebhookEventStore {

    private final PaymentWebhookEventRepository repository;
    private final Clock clock;

    /** Inserts the dedupe row and commits it. Throws DataIntegrityViolationException when the key exists. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insert(String dedupeKey, String eventType, String rawPayload) {
        return repository.saveAndFlush(PaymentWebhookEvent.builder()
                .dedupeKey(dedupeKey)
                .eventType(eventType)
                .rawPayload(rawPayload)
                .build()).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PaymentWebhookEvent find(String dedupeKey) {
        return repository.findByDedupeKey(dedupeKey).orElse(null);
    }

    /** Records a processing failure so the next delivery of the same event is reprocessed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markError(Long eventId, String message) {
        repository.findById(eventId).ifPresent(event -> {
            event.setProcessingResult(truncate("ERROR: " + message));
            event.setProcessedAt(clock.instant());
        });
    }

    static String truncate(String message) {
        if (message == null) return null;
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
