package com.spacz.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Partner withdrawal requests and vendor wallets. Wallets live in studyhall-service (next to the payments that
 * fund them); these endpoints pass through to its /internal API and record every decision in the audit log.
 * Payouts are manual: send the money by UPI / bank transfer, then mark the request PAID with the UTR.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Partner withdrawals", description = "Review and pay out partner wallet withdrawal requests")
public class AdminWithdrawalController {

    private final StudyHallServiceClient studyHallClient;
    private final AuditService auditService;

    public record ProcessingRequest(@Size(max = 500) String note) {
    }

    public record PaidRequest(@NotBlank @Size(max = 100) String payoutReference, @Size(max = 500) String note) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }

    @GetMapping("/api/admin/withdrawals")
    @Operation(summary = "Withdrawal requests with full payout details, newest first",
            description = "status: PENDING | PROCESSING | PAID | REJECTED | CANCELLED. sort e.g. requestedAt,asc")
    public JsonNode list(@RequestParam(required = false) String status,
                         @RequestParam(required = false) Long vendorId,
                         @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return studyHallClient.withdrawals(status, vendorId, pageable);
    }

    @GetMapping("/api/admin/withdrawals/{id}")
    @Operation(summary = "One withdrawal request")
    public JsonNode get(@PathVariable Long id) {
        return studyHallClient.withdrawal(id);
    }

    @PostMapping("/api/admin/withdrawals/{id}/processing")
    @Operation(summary = "Accept a PENDING request: you are sending the money (vendor can no longer cancel)")
    public JsonNode processing(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                               @Valid @RequestBody(required = false) ProcessingRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("adminId", admin.userId());
        body.put("note", request != null ? request.note() : null);
        JsonNode result = studyHallClient.withdrawalAction(id, "processing", body);
        audit(admin, AuditAction.WITHDRAWAL_PROCESSING, id, result, "Started processing withdrawal");
        return result;
    }

    @PostMapping("/api/admin/withdrawals/{id}/paid")
    @Operation(summary = "Money sent: mark PAID with the UTR / UPI transaction ID")
    public JsonNode paid(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                         @Valid @RequestBody PaidRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("adminId", admin.userId());
        body.put("payoutReference", request.payoutReference());
        body.put("note", request.note());
        JsonNode result = studyHallClient.withdrawalAction(id, "paid", body);
        audit(admin, AuditAction.WITHDRAWAL_PAID, id, result, "Paid withdrawal (ref " + request.payoutReference() + ")");
        return result;
    }

    @PostMapping("/api/admin/withdrawals/{id}/reject")
    @Operation(summary = "Decline a PENDING / PROCESSING request; the amount goes back to the vendor's wallet")
    public JsonNode reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id,
                           @Valid @RequestBody RejectRequest request) {
        JsonNode result = studyHallClient.withdrawalAction(id, "reject",
                Map.of("adminId", admin.userId(), "reason", request.reason()));
        audit(admin, AuditAction.WITHDRAWAL_REJECTED, id, result, "Rejected withdrawal: " + request.reason());
        return result;
    }

    @GetMapping("/api/admin/vendors/{vendorId}/wallet")
    @Operation(summary = "A vendor's wallet balance")
    public JsonNode wallet(@PathVariable Long vendorId) {
        return studyHallClient.vendorWallet(vendorId);
    }

    @GetMapping("/api/admin/vendors/{vendorId}/wallet/entries")
    @Operation(summary = "A vendor's wallet statement, newest first")
    public JsonNode walletEntries(@PathVariable Long vendorId,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return studyHallClient.vendorWalletEntries(vendorId, type, page, size);
    }

    private void audit(AuthenticatedUser admin, AuditAction action, Long withdrawalId, JsonNode result, String text) {
        String description = text + " " + result.path("reference").asText("#" + withdrawalId)
                + " of Rs." + result.path("amount").asText("?") + " for vendor " + result.path("vendorId").asText("?");
        auditService.record(admin, action, "WITHDRAWAL", withdrawalId,
                description.length() > 500 ? description.substring(0, 500) : description);
    }
}
