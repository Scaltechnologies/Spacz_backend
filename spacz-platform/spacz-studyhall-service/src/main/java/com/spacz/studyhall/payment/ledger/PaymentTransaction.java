package com.spacz.studyhall.payment.ledger;

import com.spacz.studyhall.payment.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * One money movement in the ledger. Written only by {@link TransactionLedger}, under the payment row lock.
 * The only change ever made to a row is a REFUND going from PENDING to SUCCESS.
 */
@Entity
@Table(name = "payment_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_txn_reference", columnNames = "transaction_reference"),
        @UniqueConstraint(name = "uk_txn_payment_type", columnNames = {"payment_id", "type"})})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shown to students, vendors and support, e.g. TXN-7KQ2M9XPA4RT. */
    @Column(name = "transaction_reference", nullable = false, length = 20, updatable = false)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private Long bookingId;

    @Column(name = "booking_reference", nullable = false, length = 20, updatable = false)
    private String bookingReference;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private Long vendorId;

    @Column(name = "study_hall_id", nullable = false, updatable = false)
    private Long studyHallId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "gateway_provider", length = 30, updatable = false)
    private String gatewayProvider;

    @Column(name = "gateway_order_id", length = 100, updatable = false)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100, updatable = false)
    private String gatewayPaymentId;

    @Column(name = "gateway_refund_id", length = 100)
    private String gatewayRefundId;

    /** Pay at Hall: the vendor account that confirmed it collected the money. */
    @Column(name = "collected_by_vendor_id", updatable = false)
    private Long collectedByVendorId;

    @Column(length = 500, updatable = false)
    private String description;

    /** When the money was received (PAYMENT) or the refund was initiated (REFUND). */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
