package com.spacz.studyhall.payment.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.controller.ApiErrorResponses;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.payment.dto.PaymentRequests.VerifyCodeRequest;
import com.spacz.studyhall.payment.dto.PaymentRequests.VerifyQrRequest;
import com.spacz.studyhall.payment.dto.VendorPayAtHallResponse;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vendor side of Pay at Hall. QR scan / code entry alone never confirms payment: only the explicit
 * /confirm call does, and it is idempotent (safe to retry on a network error).
 */
@RestController
@RequestMapping("/api/payments/pay-at-hall")
@RequiredArgsConstructor
@PreAuthorize("hasRole('VENDOR')")
@Tag(name = "Pay at Hall (vendor)", description = "Verify a student's QR or code, then confirm payment received")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class VendorPayAtHallController {

    private final PaymentService paymentService;

    @PostMapping("/verify-qr")
    @Operation(summary = "Scanned QR (with or without the SPACZ-PAY-HALL: prefix)",
            description = "404 INVALID_QR, 403 WRONG_VENDOR, 410 PAYMENT_EXPIRED, 409 PAYMENT_ALREADY_SUCCESSFUL")
    public VendorPayAtHallResponse verifyQr(@AuthenticationPrincipal AuthenticatedUser vendor,
                                            @Valid @RequestBody VerifyQrRequest request) {
        return paymentService.verifyQr(vendor.userId(), request.qrToken());
    }

    @PostMapping("/verify-code")
    @Operation(summary = "6-digit code typed in for a pending order",
            description = "422 INVALID_PAYMENT_CODE (message says attempts left), 429 TOO_MANY_ATTEMPTS after 5 wrong codes")
    public VendorPayAtHallResponse verifyCode(@AuthenticationPrincipal AuthenticatedUser vendor,
                                              @Valid @RequestBody VerifyCodeRequest request) {
        return paymentService.verifyCode(vendor.userId(), request.paymentId(), request.code());
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Payment received at the counter: confirms the booking (after verify; idempotent)",
            description = "409 PAYMENT_NOT_CONFIRMABLE when not verified yet or the booking is no longer valid")
    public VendorPayAtHallResponse confirm(@AuthenticationPrincipal AuthenticatedUser vendor, @PathVariable Long id) {
        return paymentService.confirmPayAtHall(vendor.userId(), id);
    }

    @GetMapping("/pending")
    @Operation(summary = "My open Pay-at-Hall orders, oldest first")
    public List<VendorPayAtHallResponse> pending(@AuthenticationPrincipal AuthenticatedUser vendor) {
        return paymentService.getVendorPending(vendor.userId());
    }

    @GetMapping("/history")
    @Operation(summary = "All my Pay-at-Hall orders, newest first")
    public PageResponse<VendorPayAtHallResponse> history(@AuthenticationPrincipal AuthenticatedUser vendor,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        // The derived query already orders by createdAt desc: no extra Sort.
        return paymentService.getVendorHistory(vendor.userId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }
}
