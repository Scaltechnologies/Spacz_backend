package com.spacz.studyhall.wallet.entity;

import com.spacz.studyhall.exception.ConflictException;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** A vendor's request to be paid out of the wallet, with a snapshot of the account it is paid to. */
@Entity
@Table(name = "vendor_withdrawals")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor(access = AccessLevel.PRIVATE) @Builder
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shown to the vendor and admins, e.g. WDR-7KQ2M9XPA4RT. */
    @Column(nullable = false, length = 20, unique = true, updatable = false)
    private String reference;

    @Column(name = "vendor_id", nullable = false, updatable = false)
    private Long vendorId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status;

    @Column(name = "payout_account_id", nullable = false, updatable = false)
    private Long payoutAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", nullable = false, length = 20, updatable = false)
    private PayoutMethod payoutMethod;

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

    @Column(name = "vendor_note", length = 255, updatable = false)
    private String vendorNote;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** UTR / bank or UPI transfer reference, set when PAID. */
    @Column(name = "payout_reference", length = 100)
    private String payoutReference;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static Withdrawal request(String reference, Long vendorId, BigDecimal amount, PayoutAccount account,
                                     String vendorNote, Instant now) {
        return Withdrawal.builder()
                .reference(reference)
                .vendorId(vendorId)
                .amount(amount)
                .status(WithdrawalStatus.PENDING)
                .payoutAccountId(account.getId())
                .payoutMethod(account.getMethod())
                .upiId(account.getUpiId())
                .accountHolderName(account.getAccountHolderName())
                .accountNumber(account.getAccountNumber())
                .ifscCode(account.getIfscCode())
                .bankName(account.getBankName())
                .vendorNote(vendorNote)
                .requestedAt(now)
                .build();
    }

    public void markProcessing(Long adminId, String note, Instant now) {
        if (status != WithdrawalStatus.PENDING) {
            throw notAllowed("mark as processing");
        }
        status = WithdrawalStatus.PROCESSING;
        processedByAdminId = adminId;
        processingAt = now;
        if (note != null) {
            adminNote = note;
        }
    }

    public void markPaid(Long adminId, String payoutReference, String note, Instant now) {
        if (!status.isOpen()) {
            throw notAllowed("mark as paid");
        }
        status = WithdrawalStatus.PAID;
        this.payoutReference = payoutReference;
        processedByAdminId = adminId;
        paidAt = now;
        if (note != null) {
            adminNote = note;
        }
    }

    public void reject(Long adminId, String reason, Instant now) {
        if (!status.isOpen()) {
            throw notAllowed("reject");
        }
        status = WithdrawalStatus.REJECTED;
        rejectionReason = reason;
        processedByAdminId = adminId;
        rejectedAt = now;
    }

    public void cancelByVendor(Instant now) {
        if (status != WithdrawalStatus.PENDING) {
            throw notAllowed("cancel");
        }
        status = WithdrawalStatus.CANCELLED;
        cancelledAt = now;
    }

    private ConflictException notAllowed(String action) {
        return new ConflictException("WITHDRAWAL_STATUS_CONFLICT",
                "Cannot " + action + " withdrawal " + reference + " in status " + status);
    }
}
