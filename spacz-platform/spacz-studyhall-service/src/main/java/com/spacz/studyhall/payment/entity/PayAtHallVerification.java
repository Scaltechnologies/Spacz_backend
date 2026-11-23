package com.spacz.studyhall.payment.entity;

import com.spacz.studyhall.payment.enums.VerificationMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One-to-one companion to a {@link Payment} with {@code paymentMethod == PAY_AT_HALL}. Kept as a
 * separate table (not columns on Payment) so the online-payment columns are never touched by this
 * feature. Only hashes of the QR token and 6-digit code are ever stored: the raw values exist only in
 * the create response returned once to the student, never logged, never persisted, never returned again.
 */
@Entity
@Table(name = "pay_at_hall_verifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayAtHallVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "qr_token_hash", nullable = false, unique = true, length = 128)
    private String qrTokenHash;

    @Column(name = "verification_code_hash", nullable = false, length = 128)
    private String verificationCodeHash;

    /** Failed verify-code attempts since the last lockout reset. Never incremented for QR scans: a QR
     *  token is unguessable, so brute-forcing it isn't a realistic attack the way a 6-digit code is. */
    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    /** Set once attemptCount hits the max; verify-code is refused until this passes. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_method", length = 10)
    private VerificationMethod confirmationMethod;

    @Column(name = "confirmed_by_vendor_id")
    private Long confirmedByVendorId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
