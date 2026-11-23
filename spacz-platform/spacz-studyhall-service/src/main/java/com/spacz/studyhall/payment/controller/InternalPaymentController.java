package com.spacz.studyhall.payment.controller;

import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.AuditEventView;
import com.spacz.studyhall.payment.dto.PaymentAdminViews.PaymentView;
import com.spacz.studyhall.payment.dto.PaymentRequests.PayAtHallSwitchRequest;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Called by admin-service only (internal API key; the gateway denies /internal/** from outside). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Internal - Payments", description = "Payment views and the Pay-at-Hall switch for admin-service")
@SecurityRequirement(name = "internalApiKey")
public class InternalPaymentController {

    private final PaymentService paymentService;
    private final VendorProfileRepository vendorProfileRepository;

    @GetMapping("/internal/payments/{paymentId}/audit-events")
    @Operation(summary = "Audit trail of one payment (never contains QR tokens or codes)")
    public List<AuditEventView> auditEvents(@PathVariable Long paymentId) {
        return paymentService.auditEvents(paymentId);
    }

    @GetMapping("/internal/payments")
    @Operation(summary = "All payments of a booking, newest first, with their gateway attempts")
    public List<PaymentView> forBooking(@RequestParam Long bookingId) {
        return paymentService.paymentsForBooking(bookingId);
    }

    @PatchMapping("/internal/vendors/{vendorId}/pay-at-hall")
    @Transactional
    @Operation(summary = "Enable or disable Pay at Hall at a vendor's study halls (vendorId = auth account ID)")
    public Map<String, Object> setPayAtHall(@PathVariable Long vendorId, @Valid @RequestBody PayAtHallSwitchRequest request) {
        VendorProfile vendor = vendorProfileRepository.findByVendorId(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));
        vendor.changePayAtHall(request.enabled());
        return Map.of("vendorId", vendorId, "payAtHallEnabled", vendor.isPayAtHallEnabled());
    }
}
