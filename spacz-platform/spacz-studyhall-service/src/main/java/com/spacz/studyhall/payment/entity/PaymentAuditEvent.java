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
 * Append-only trail of everything that happens to a {@link Payment}: creation, QR scans, code
 * attempts, vendor confirmation, expiry, refunds. Multiple independent actors (user, vendor, system)
 * touch the same session over time. Never stores the raw QR token or 6-digit code: only actions,
 * status transitions and non-sensitive metadata.
 */
@Entity
@Table(name = "payment_audit_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** USER, VENDOR, ADMIN, SYSTEM */
    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    /** e.g. PAYMENT_CREATED, QR_SCANNED, CODE_VERIFIED, CODE_VERIFICATION_FAILED, PAYMENT_CONFIRMED,
     *  PAYMENT_EXPIRED, PAYMENT_CANCELLED, REFUND_REQUIRED, REFUNDED */
    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "previous_status", length = 40)
    private String previousStatus;

    @Column(name = "new_status", length = 40)
    private String newStatus;

    @Column(length = 500)
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
