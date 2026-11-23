package com.spacz.studyhall.wallet.entity;

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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** One immutable line of a vendor's wallet (Flyway V14). The balance is the sum of the amounts. */
@Entity
@Table(name = "vendor_wallet_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_entry_payment", columnNames = {"payment_id", "type"}),
        @UniqueConstraint(name = "uk_wallet_entry_withdrawal", columnNames = {"withdrawal_id", "type"})})
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class WalletEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private WalletEntryType type;

    /** Credit > 0, debit < 0. */
    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    @Builder.Default
    private String currency = "INR";

    /** Counts towards the withdrawable balance from this moment. */
    @Column(name = "available_at", nullable = false, updatable = false)
    private Instant availableAt;

    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "booking_id", updatable = false)
    private Long bookingId;

    @Column(name = "booking_reference", length = 20, updatable = false)
    private String bookingReference;

    @Column(name = "withdrawal_id", updatable = false)
    private Long withdrawalId;

    @Column(length = 500, updatable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
