package com.spacz.studyhall.payment.entity;

import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One payment intent for a single booking. A Payment owns one or more {@link PaymentAttempt} rows
 * (one per gateway charge try, see the retry flow). {@code status} reflects the authoritative,
 * backend-confirmed outcome; it is never set from a frontend claim alone, and only through
 * {@link #transitionTo}. Once SUCCESS the row (and the booking confirmation it caused) is immutable.
 */
@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "study_hall_id", nullable = false)
    private Long studyHallId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    /** The hall owner's auth account ID (VendorProfile.vendorId). */
    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "booking_reference", nullable = false, length = 20)
    private String bookingReference;

    @Column(nullable = false, length = 255)
    private String description;

    /** Authoritative amount the gateway must confirm was charged: Booking.totalPrice, never from the client. */
    @Column(name = "final_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Client-supplied Idempotency-Key from payment creation, unique when present. */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    /** Session TTL: past this a still-open payment is reconciled/expired, never auto-failed. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "gateway_refund_id", length = 100)
    private String gatewayRefundId;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: defense in depth alongside the pessimistic row lock taken during finalize. */
    @Version
    private Long version;

    /** The only way to change status; an illegal move is a programming error, never silently applied. */
    public void transitionTo(PaymentStatus next) {
        if (status != null && !status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal payment status transition " + status + " -> " + next
                    + " for payment " + id);
        }
        status = next;
    }
}
