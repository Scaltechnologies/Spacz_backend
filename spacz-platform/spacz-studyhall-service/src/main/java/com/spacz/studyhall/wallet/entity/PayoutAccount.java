package com.spacz.studyhall.wallet.entity;

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

/** A UPI ID or bank account a vendor can withdraw to. Deleting only deactivates it (withdrawals keep a snapshot). */
@Entity
@Table(name = "vendor_payout_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayoutAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PayoutMethod method;

    @Column(name = "upi_id", length = 100, updatable = false)
    private String upiId;

    @Column(name = "account_holder_name", length = 120, updatable = false)
    private String accountHolderName;

    @Column(name = "account_number", length = 20, updatable = false)
    private String accountNumber;

    @Column(name = "ifsc_code", length = 11, updatable = false)
    private String ifscCode;

    @Column(name = "bank_name", length = 100, updatable = false)
    private String bankName;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAccount;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
