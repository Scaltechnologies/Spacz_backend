package com.spacz.studyhall.wallet.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.controller.ApiErrorResponses;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.wallet.dto.WalletRequests.AddPayoutAccountRequest;
import com.spacz.studyhall.wallet.dto.WalletRequests.CreateWithdrawalRequest;
import com.spacz.studyhall.wallet.dto.WalletResponses.PayoutAccountResponse;
import com.spacz.studyhall.wallet.dto.WalletResponses.WalletEntryResponse;
import com.spacz.studyhall.wallet.dto.WalletResponses.WalletSummaryResponse;
import com.spacz.studyhall.wallet.dto.WalletResponses.WithdrawalResponse;
import com.spacz.studyhall.wallet.entity.WalletEntryType;
import com.spacz.studyhall.wallet.entity.WithdrawalStatus;
import com.spacz.studyhall.wallet.service.PayoutAccountService;
import com.spacz.studyhall.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Partner app: wallet balance and statement, payout accounts (UPI / bank) and withdrawal requests. */
@RestController
@RequestMapping("/api/vendors/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@Tag(name = "Partner wallet", description = "Wallet balance, payout accounts and withdrawals of the signed-in vendor")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class VendorWalletController {

    private final WalletService walletService;
    private final PayoutAccountService payoutAccountService;

    // ── wallet ──

    @GetMapping("/wallet")
    @Operation(summary = "Wallet balance: available, pending (settlement hold), in withdrawal, totals, limits")
    public WalletSummaryResponse wallet(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return walletService.summary(vendor.userId());
    }

    @GetMapping("/wallet/entries")
    @Operation(summary = "Wallet statement, newest first",
            description = "type: BOOKING_EARNING | BOOKING_REFUND | PAY_AT_HALL_FEE | WITHDRAWAL | WITHDRAWAL_REVERSAL")
    public PageResponse<WalletEntryResponse> entries(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                     @RequestParam(required = false) WalletEntryType type,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return walletService.statement(vendor.userId(), type, page, size);
    }

    // ── payout accounts ──

    @GetMapping("/payout-accounts")
    @Operation(summary = "My UPI IDs and bank accounts (default first; account numbers masked)")
    public List<PayoutAccountResponse> payoutAccounts(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return payoutAccountService.list(vendor.userId());
    }

    @PostMapping("/payout-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a UPI ID or bank account",
            description = "422 INVALID_PAYOUT_ACCOUNT (bad UPI / account number / IFSC), PAYOUT_ACCOUNT_LIMIT; 409 DUPLICATE_RESOURCE")
    public PayoutAccountResponse addPayoutAccount(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                  @Valid @RequestBody AddPayoutAccountRequest request) {
        return payoutAccountService.add(vendor.userId(), request);
    }

    @PatchMapping("/payout-accounts/{id}/default")
    @Operation(summary = "Make this the default payout account")
    public PayoutAccountResponse makeDefault(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return payoutAccountService.makeDefault(vendor.userId(), id);
    }

    @DeleteMapping("/payout-accounts/{id}")
    @Operation(summary = "Remove a payout account (withdrawals already requested to it are not affected)")
    public ResponseEntity<Void> deletePayoutAccount(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                    @PathVariable Long id) {
        payoutAccountService.delete(vendor.userId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── withdrawals ──

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request a withdrawal of (part of) the available balance",
            description = "The amount is reserved at once. One open request at a time. 422 VENDOR_NOT_APPROVED, "
                    + "WITHDRAWAL_BELOW_MINIMUM, WITHDRAWAL_ABOVE_MAXIMUM, INSUFFICIENT_BALANCE; 409 WITHDRAWAL_IN_PROGRESS")
    public WithdrawalResponse requestWithdrawal(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                @Valid @RequestBody CreateWithdrawalRequest request) {
        return walletService.requestWithdrawal(vendor.userId(), request);
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "My withdrawals, newest first",
            description = "status: PENDING | PROCESSING | PAID | REJECTED | CANCELLED")
    public PageResponse<WithdrawalResponse> withdrawals(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                        @RequestParam(required = false) WithdrawalStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return walletService.myWithdrawals(vendor.userId(), status, page, size);
    }

    @GetMapping("/withdrawals/{id}")
    @Operation(summary = "One of my withdrawals")
    public WithdrawalResponse withdrawal(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return walletService.myWithdrawal(vendor.userId(), id);
    }

    @PostMapping("/withdrawals/{id}/cancel")
    @Operation(summary = "Cancel a PENDING withdrawal; the amount goes back to the wallet",
            description = "409 WITHDRAWAL_STATUS_CONFLICT once an admin has started processing it")
    public WithdrawalResponse cancel(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return walletService.cancel(vendor.userId(), id);
    }
}
