package com.spacz.studyhall.wallet.dto;

import com.spacz.studyhall.wallet.entity.PayoutMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request bodies of the wallet API. Field formats are checked in PayoutAccountService (per method). */
public final class WalletRequests {

    private WalletRequests() {
    }

    @Schema(description = "UPI: upiId. BANK_ACCOUNT: accountHolderName, accountNumber, ifscCode (bankName optional).")
    public record AddPayoutAccountRequest(
            @NotNull PayoutMethod method,
            @Schema(example = "focushall@okaxis") @Size(max = 100) String upiId,
            @Schema(example = "Focus Study Hall") @Size(max = 120) String accountHolderName,
            @Schema(example = "50100123456789") @Size(max = 20) String accountNumber,
            @Schema(example = "HDFC0001234") @Size(max = 11) String ifscCode,
            @Schema(example = "HDFC Bank") @Size(max = 100) String bankName,
            @Schema(description = "Make it the default account (the first account is always the default)")
            Boolean makeDefault) {
    }

    public record CreateWithdrawalRequest(
            @NotNull @DecimalMin(value = "1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull Long payoutAccountId,
            @Size(max = 255) String note) {
    }

    /** admin-service → studyhall: an admin accepted the request and is sending the money. */
    public record MarkProcessingRequest(@NotNull Long adminId, @Size(max = 500) String note) {
    }

    /** admin-service → studyhall: the money was sent. */
    public record MarkPaidRequest(@NotNull Long adminId,
                                  @Schema(description = "UTR / UPI transaction ID of the transfer", example = "UTR412345678901")
                                  @NotBlank @Size(max = 100) String payoutReference,
                                  @Size(max = 500) String note) {
    }

    /** admin-service → studyhall: the request is refused; the amount goes back to the wallet. */
    public record RejectRequest(@NotNull Long adminId, @NotBlank @Size(max = 500) String reason) {
    }
}
