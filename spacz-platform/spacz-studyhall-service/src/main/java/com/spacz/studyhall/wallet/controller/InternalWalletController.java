package com.spacz.studyhall.wallet.controller;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.wallet.dto.WalletRequests.MarkPaidRequest;
import com.spacz.studyhall.wallet.dto.WalletRequests.MarkProcessingRequest;
import com.spacz.studyhall.wallet.dto.WalletRequests.RejectRequest;
import com.spacz.studyhall.wallet.dto.WalletResponses.AdminWithdrawalView;
import com.spacz.studyhall.wallet.dto.WalletResponses.WalletEntryResponse;
import com.spacz.studyhall.wallet.dto.WalletResponses.WalletSummaryResponse;
import com.spacz.studyhall.wallet.entity.WalletEntryType;
import com.spacz.studyhall.wallet.entity.WithdrawalStatus;
import com.spacz.studyhall.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Called by admin-service only (internal API key; the gateway denies /internal/** from outside). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Internal - Vendor wallets", description = "Withdrawal processing and vendor wallets for admin-service")
@SecurityRequirement(name = "internalApiKey")
public class InternalWalletController {

    private final WalletService walletService;

    @GetMapping("/internal/withdrawals")
    @Operation(summary = "Withdrawal requests (full payout details), newest first by default")
    public PageResponse<AdminWithdrawalView> withdrawals(@RequestParam(required = false) WithdrawalStatus status,
                                                         @RequestParam(required = false) Long vendorId,
                                                         @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return walletService.adminList(status, vendorId, pageable);
    }

    @GetMapping("/internal/withdrawals/{id}")
    @Operation(summary = "One withdrawal request")
    public AdminWithdrawalView withdrawal(@PathVariable Long id) {
        return walletService.adminGet(id);
    }

    @PostMapping("/internal/withdrawals/{id}/processing")
    @Operation(summary = "PENDING -> PROCESSING (the admin is sending the money)")
    public AdminWithdrawalView processing(@PathVariable Long id, @Valid @RequestBody MarkProcessingRequest request) {
        return walletService.markProcessing(id, request);
    }

    @PostMapping("/internal/withdrawals/{id}/paid")
    @Operation(summary = "PENDING / PROCESSING -> PAID, with the UTR / transfer reference")
    public AdminWithdrawalView paid(@PathVariable Long id, @Valid @RequestBody MarkPaidRequest request) {
        return walletService.markPaid(id, request);
    }

    @PostMapping("/internal/withdrawals/{id}/reject")
    @Operation(summary = "PENDING / PROCESSING -> REJECTED; the amount goes back to the vendor's wallet")
    public AdminWithdrawalView reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return walletService.reject(id, request);
    }

    @GetMapping("/internal/vendors/{vendorId}/wallet")
    @Operation(summary = "A vendor's wallet balance (vendorId = auth account ID)")
    public WalletSummaryResponse wallet(@PathVariable Long vendorId) {
        return walletService.summary(vendorId);
    }

    @GetMapping("/internal/vendors/{vendorId}/wallet/entries")
    @Operation(summary = "A vendor's wallet statement, newest first")
    public PageResponse<WalletEntryResponse> entries(@PathVariable Long vendorId,
                                                     @RequestParam(required = false) WalletEntryType type,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return walletService.statement(vendorId, type, page, size);
    }
}
