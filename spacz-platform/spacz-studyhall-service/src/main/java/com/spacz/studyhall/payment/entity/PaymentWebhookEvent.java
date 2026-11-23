package com.spacz.studyhall.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Dedup log for inbound gateway webhook deliveries. {@code dedupeKey} is the gateway's own event id
 * when it supplies one, else a derived key (orderId:paymentId:eventType). The unique constraint makes
 * duplicate/replayed deliveries a no-op at the database level, not merely an application-level check.
 */
@Entity
@Table(name = "payment_webhook_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 200)
    private String dedupeKey;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payment_attempt_id")
    private Long paymentAttemptId;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    /** null (received / being processed), PROCESSED, IGNORED_NO_ORDER_REFERENCE, IGNORED_UNKNOWN_PAYMENT, ERROR: ... */
    @Column(name = "processing_result", length = 1000)
    private String processingResult;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** A row is final once processed or deliberately ignored; null and ERROR rows are reprocessed. */
    public boolean isFinal() {
        return processingResult != null && !processingResult.startsWith("ERROR");
    }
}
