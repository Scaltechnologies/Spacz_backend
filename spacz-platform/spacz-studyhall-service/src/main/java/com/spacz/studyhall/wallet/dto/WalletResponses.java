package com.spacz.studyhall.wallet.dto;

import com.spacz.studyhall.wallet.entity.PayoutAccount;
import com.spacz.studyhall.wallet.entity.PayoutMethod;
import com.spacz.studyhall.wallet.entity.WalletEntry;
import com.spacz.studyhall.wallet.entity.WalletEntryType;
import com.spacz.studyhall.wallet.entity.Withdrawal;
import com.spacz.studyhall.wallet.entity.WithdrawalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** Response bodies of the wallet API. Vendors see masked bank account numbers; admins see them in full. */
public final class WalletResponses {

    private WalletResponses() {
    }

    public record WalletSummaryResponse(
            String currency,
            @Schema(description = "Can be withdrawn now") BigDecimal availableBalance,
            @Schema(description = "Online earnings still in the settlement hold") BigDecimal pendingBalance,
            @Schema(description = "Reserved by open withdrawal requests (already deducted from availableBalance)")
            BigDecimal inWithdrawal,
            @Schema(description = "All online earnings credited so far (before refunds)") BigDecimal totalEarned,
            @Schema(description = "All withdrawals paid out so far") BigDecimal totalWithdrawn,
            BigDecimal minWithdrawalAmount,
            BigDecimal maxWithdrawalAmount,
            @Schema(description = "Hours an online earning stays pending") long settlementHoldHours,
            @Schema(description = "true when a withdrawal can be requested right now") boolean canWithdraw,
            @Schema(description = "Why not, when canWithdraw is false: VENDOR_NOT_APPROVED, WITHDRAWAL_IN_PROGRESS, "
                    + "BELOW_MINIMUM, NO_PAYOUT_ACCOUNT") String withdrawBlockedReason,
            @Schema(description = "The open (PENDING / PROCESSING) request, if any") WithdrawalResponse openWithdrawal) {
    }

    public record WalletEntryResponse(Long id, WalletEntryType type,
                                      @Schema(description = "Credit > 0, debit < 0") BigDecimal amount,
                                      String currency, String description, Long bookingId, String bookingReference,
                                      Long withdrawalId,
                                      @Schema(description = "When it becomes / became withdrawable") Instant availableAt,
                                      @Schema(description = "Still in the settlement hold") boolean pending,
                                      Instant createdAt) {

        public static WalletEntryResponse from(WalletEntry e, Instant now) {
            return new WalletEntryResponse(e.getId(), e.getType(), e.getAmount(), e.getCurrency(), e.getDescription(),
                    e.getBookingId(), e.getBookingReference(), e.getWithdrawalId(), e.getAvailableAt(),
                    e.getAvailableAt().isAfter(now), e.getCreatedAt());
        }
    }

    public record PayoutAccountResponse(Long id, PayoutMethod method, String upiId, String accountHolderName,
                                        @Schema(example = "XXXXXXXXXX6789") String accountNumberMasked,
                                        String ifscCode, String bankName,
                                        @Schema(example = "HDFC Bank ••6789") String displayName,
                                        boolean isDefault, Instant createdAt) {

        public static PayoutAccountResponse from(PayoutAccount a) {
            return new PayoutAccountResponse(a.getId(), a.getMethod(), a.getUpiId(), a.getAccountHolderName(),
                    mask(a.getAccountNumber()), a.getIfscCode(), a.getBankName(),
                    payoutLabel(a.getMethod(), a.getUpiId(), a.getBankName(), a.getAccountNumber()),
                    a.isDefaultAccount(), a.getCreatedAt());
        }
    }

    public record WithdrawalResponse(Long id, String reference, BigDecimal amount, String currency,
                                     WithdrawalStatus status, Long payoutAccountId, PayoutMethod payoutMethod,
                                     String upiId, String accountHolderName, String accountNumberMasked,
                                     String ifscCode, String bankName, String payoutDisplayName, String note,
                                     String rejectionReason,
                                     @Schema(description = "UTR / transfer reference once PAID") String payoutReference,
                                     Instant requestedAt, Instant processingAt, Instant paidAt, Instant rejectedAt,
                                     Instant cancelledAt) {

        public static WithdrawalResponse from(Withdrawal w) {
            return new WithdrawalResponse(w.getId(), w.getReference(), w.getAmount(), w.getCurrency(), w.getStatus(),
                    w.getPayoutAccountId(), w.getPayoutMethod(), w.getUpiId(), w.getAccountHolderName(),
                    mask(w.getAccountNumber()), w.getIfscCode(), w.getBankName(),
                    payoutLabel(w.getPayoutMethod(), w.getUpiId(), w.getBankName(), w.getAccountNumber()),
                    w.getVendorNote(), w.getRejectionReason(), w.getPayoutReference(), w.getRequestedAt(),
                    w.getProcessingAt(), w.getPaidAt(), w.getRejectedAt(), w.getCancelledAt());
        }
    }

    /** Admin view: full payout details plus who the vendor is. */
    public record AdminWithdrawalView(Long id, String reference, Long vendorId, String vendorBusinessName,
                                      String vendorPhone, BigDecimal amount, String currency, WithdrawalStatus status,
                                      PayoutMethod payoutMethod, String upiId, String accountHolderName,
                                      String accountNumber, String ifscCode, String bankName, String vendorNote,
                                      String adminNote, String rejectionReason, String payoutReference,
                                      Long processedByAdminId, Instant requestedAt, Instant processingAt,
                                      Instant paidAt, Instant rejectedAt, Instant cancelledAt) {

        public static AdminWithdrawalView from(Withdrawal w, String businessName, String phone) {
            return new AdminWithdrawalView(w.getId(), w.getReference(), w.getVendorId(), businessName, phone,
                    w.getAmount(), w.getCurrency(), w.getStatus(), w.getPayoutMethod(), w.getUpiId(),
                    w.getAccountHolderName(), w.getAccountNumber(), w.getIfscCode(), w.getBankName(),
                    w.getVendorNote(), w.getAdminNote(), w.getRejectionReason(), w.getPayoutReference(),
                    w.getProcessedByAdminId(), w.getRequestedAt(), w.getProcessingAt(), w.getPaidAt(),
                    w.getRejectedAt(), w.getCancelledAt());
        }
    }

    static String mask(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        int keep = Math.min(4, accountNumber.length());
        return "X".repeat(accountNumber.length() - keep) + accountNumber.substring(accountNumber.length() - keep);
    }

    static String payoutLabel(PayoutMethod method, String upiId, String bankName, String accountNumber) {
        if (method == PayoutMethod.UPI) {
            return upiId;
        }
        String last4 = accountNumber == null ? "" : accountNumber.substring(Math.max(0, accountNumber.length() - 4));
        return (bankName != null && !bankName.isBlank() ? bankName : "Bank account") + " ••" + last4;
    }
}
