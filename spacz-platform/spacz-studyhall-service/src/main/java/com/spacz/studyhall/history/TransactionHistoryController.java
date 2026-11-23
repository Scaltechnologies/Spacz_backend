package com.spacz.studyhall.history;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.controller.ApiErrorResponses;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.history.HistoryService.Query;
import com.spacz.studyhall.history.HistoryViews.TransactionSummary;
import com.spacz.studyhall.history.HistoryViews.TransactionView;
import com.spacz.studyhall.payment.dto.PaymentResponse;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import com.spacz.studyhall.payment.ledger.TransactionStatus;
import com.spacz.studyhall.payment.ledger.TransactionType;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Transaction and payment history for students (/api/payments/...) and vendors (/api/vendors/me/...).
 * Admin views are served to admin-service by {@link InternalHistoryController}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Transaction history", description = "Money ledger (payments received, refunds) and payment history")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class TransactionHistoryController {

    private final HistoryService historyService;
    private final PaymentService paymentService;

    // ── Students ─────────────────────────────────────────────────────────────

    @GetMapping("/api/payments/transactions")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "My transactions: money paid (PAYMENT) and refunds (REFUND), newest first",
            description = "Failed or abandoned payments moved no money and are not listed here; see "
                    + "GET /api/payments/history. from/to are dates (yyyy-MM-dd, inclusive) in the business time zone. "
                    + "sort: occurredAt | amount | completedAt.")
    public PageResponse<TransactionView> myTransactions(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @RequestParam(required = false) TransactionType type,
                                                        @RequestParam(required = false) TransactionStatus status,
                                                        @RequestParam(required = false) Long bookingId,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return historyService.userTransactions(user.userId(),
                new Query(null, bookingId, type, status, null, from, to), pageable);
    }

    @GetMapping("/api/payments/transactions/{transactionId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "One of my transactions (receipt)")
    public TransactionView myTransaction(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable Long transactionId) {
        return historyService.userTransaction(user.userId(), transactionId);
    }

    @GetMapping("/api/payments/history")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "All my payments of every status (SUCCESS, FAILED, CANCELLED, EXPIRED, REFUNDED, ...), newest first")
    public PageResponse<PaymentResponse> myPayments(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam(required = false) PaymentStatus status,
                                                    @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.myPayments(user.userId(), status, pageable);
    }

    // ── Vendors ──────────────────────────────────────────────────────────────

    @GetMapping("/api/vendors/me/transactions")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Transactions at my study halls (online and Pay at Hall), newest first",
            description = "from/to are dates (yyyy-MM-dd, inclusive). sort: occurredAt | amount | completedAt.")
    public PageResponse<TransactionView> vendorTransactions(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                            @RequestParam(required = false) Long studyHallId,
                                                            @RequestParam(required = false) TransactionType type,
                                                            @RequestParam(required = false) TransactionStatus status,
                                                            @RequestParam(required = false) PaymentMethod paymentMethod,
                                                            @RequestParam(required = false) Long bookingId,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return historyService.vendorTransactions(vendor.userId(),
                new Query(studyHallId, bookingId, type, status, paymentMethod, from, to), pageable);
    }

    @GetMapping("/api/vendors/me/transactions/{transactionId:\\d+}")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "One transaction at my study halls (receipt)")
    public TransactionView vendorTransaction(@AuthenticationPrincipal AuthenticatedUser vendor,
                                             @PathVariable Long transactionId) {
        return historyService.vendorTransaction(vendor.userId(), transactionId);
    }

    @GetMapping("/api/vendors/me/transactions/summary")
    @PreAuthorize("hasRole('VENDOR')")
    @Operation(summary = "Totals at my study halls: collected, refunded, refunds pending, net, collected per method")
    public TransactionSummary vendorSummary(@AuthenticationPrincipal AuthenticatedUser vendor,
                                            @RequestParam(required = false) Long studyHallId,
                                            @RequestParam(required = false) PaymentMethod paymentMethod,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return historyService.vendorSummary(vendor.userId(),
                new Query(studyHallId, null, null, null, paymentMethod, from, to));
    }
}
