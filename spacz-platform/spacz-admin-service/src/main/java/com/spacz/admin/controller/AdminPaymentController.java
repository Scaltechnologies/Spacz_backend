package com.spacz.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.StudyHallServiceClient.TransactionQuery;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin views of payments and the per-vendor Pay-at-Hall switch. Payments live in studyhall-service
 * (with the bookings they confirm); these endpoints pass through to its /internal API.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Payments", description = "Booking payments, audit trails and Pay-at-Hall settings")
public class AdminPaymentController {

    private final StudyHallServiceClient studyHallClient;
    private final AuditService auditService;

    public record PayAtHallRequest(@NotNull Boolean enabled) {
    }

    @GetMapping("/api/admin/payments/{paymentId}/audit-events")
    @Operation(summary = "Audit trail of one payment (never contains Pay-at-Hall QR tokens or codes)")
    public JsonNode auditEvents(@PathVariable Long paymentId) {
        return studyHallClient.paymentAuditEvents(paymentId);
    }

    @GetMapping("/api/admin/bookings/{bookingId}/payments")
    @Operation(summary = "All payments of one booking, newest first (status, method, gateway ids, refund id)")
    public JsonNode bookingPayments(@PathVariable Long bookingId) {
        return studyHallClient.paymentsForBooking(bookingId);
    }

    @GetMapping("/api/admin/bookings/{bookingId}/history")
    @Operation(summary = "A booking's full history: status changes (who, when, why), payments and transactions")
    public JsonNode bookingHistory(@PathVariable Long bookingId) {
        return studyHallClient.bookingHistory(bookingId);
    }

    @GetMapping("/api/admin/transactions")
    @Operation(summary = "Platform transaction ledger: payments received and refunds, newest first",
            description = "type PAYMENT|REFUND, status PENDING|SUCCESS, paymentMethod ONLINE|PAY_AT_HALL, "
                    + "from/to dates (yyyy-MM-dd, inclusive). sort: occurredAt | amount | completedAt.")
    public JsonNode transactions(@RequestParam(required = false) Long userId,
                                 @RequestParam(required = false) Long vendorId,
                                 @RequestParam(required = false) Long studyHallId,
                                 @RequestParam(required = false) Long bookingId,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String paymentMethod,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return studyHallClient.transactions(new TransactionQuery(userId, vendorId, studyHallId, bookingId, type, status,
                paymentMethod, from, to), pageable);
    }

    @GetMapping("/api/admin/transactions/summary")
    @Operation(summary = "Ledger totals: collected, refunded, refunds pending, net, collected per payment method")
    public JsonNode transactionSummary(@RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) Long vendorId,
                                       @RequestParam(required = false) Long studyHallId,
                                       @RequestParam(required = false) String paymentMethod,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return studyHallClient.transactionSummary(new TransactionQuery(userId, vendorId, studyHallId, null, null, null,
                paymentMethod, from, to));
    }

    @PatchMapping("/api/admin/vendors/{vendorId}/pay-at-hall")
    @Operation(summary = "Enable or disable Pay at Hall for a vendor's study halls (default enabled)")
    public JsonNode setPayAtHall(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long vendorId,
                                 @Valid @RequestBody PayAtHallRequest request) {
        JsonNode result = studyHallClient.setPayAtHall(vendorId, request.enabled());
        auditService.record(admin, AuditAction.VENDOR_PAY_AT_HALL_UPDATED, "VENDOR", vendorId,
                (request.enabled() ? "Enabled" : "Disabled") + " Pay at Hall for vendor " + vendorId);
        return result;
    }
}
