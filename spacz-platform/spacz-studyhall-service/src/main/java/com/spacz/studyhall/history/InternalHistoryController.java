package com.spacz.studyhall.history;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.history.HistoryService.Query;
import com.spacz.studyhall.history.HistoryViews.BookingHistory;
import com.spacz.studyhall.history.HistoryViews.TransactionSummary;
import com.spacz.studyhall.history.HistoryViews.TransactionView;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.ledger.TransactionStatus;
import com.spacz.studyhall.payment.ledger.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Called by admin-service only (internal API key; the gateway denies /internal/** from outside). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Internal - History", description = "Platform-wide transaction ledger and booking timelines for admin-service")
@SecurityRequirement(name = "internalApiKey")
public class InternalHistoryController {

    private final HistoryService historyService;

    @GetMapping("/internal/transactions")
    @Operation(summary = "All ledger transactions, filtered, newest first")
    public PageResponse<TransactionView> transactions(@RequestParam(required = false) Long userId,
                                                      @RequestParam(required = false) Long vendorId,
                                                      @RequestParam(required = false) Long studyHallId,
                                                      @RequestParam(required = false) Long bookingId,
                                                      @RequestParam(required = false) TransactionType type,
                                                      @RequestParam(required = false) TransactionStatus status,
                                                      @RequestParam(required = false) PaymentMethod paymentMethod,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return historyService.allTransactions(userId, vendorId,
                new Query(studyHallId, bookingId, type, status, paymentMethod, from, to), pageable);
    }

    @GetMapping("/internal/transactions/summary")
    @Operation(summary = "Ledger totals over the same filters")
    public TransactionSummary summary(@RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) Long vendorId,
                                      @RequestParam(required = false) Long studyHallId,
                                      @RequestParam(required = false) PaymentMethod paymentMethod,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return historyService.allSummary(userId, vendorId,
                new Query(studyHallId, null, null, null, paymentMethod, from, to));
    }

    @GetMapping("/internal/bookings/{bookingId}/history")
    @Operation(summary = "A booking's status history, payments and transactions, oldest first")
    public BookingHistory bookingHistory(@PathVariable Long bookingId) {
        return historyService.bookingHistoryForAdmin(bookingId);
    }
}
