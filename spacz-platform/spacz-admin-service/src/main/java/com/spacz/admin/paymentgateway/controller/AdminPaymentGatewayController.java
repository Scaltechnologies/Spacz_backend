package com.spacz.admin.paymentgateway.controller;

import com.spacz.admin.paymentgateway.dto.PaymentGatewayDtos.ConfigResponse;
import com.spacz.admin.paymentgateway.dto.PaymentGatewayDtos.UpdateRequest;
import com.spacz.admin.paymentgateway.service.PaymentGatewayConfigService;
import com.spacz.admin.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets an ADMIN paste/rotate payment gateway credentials (Razorpay test or live keys) from the admin
 * dashboard, with no developer, code change or redeploy. studyhall-service picks up changes within its
 * credentials cache TTL (see its PaymentGatewayConfigClient), not instantly, but without a restart.
 */
@RestController
@RequestMapping("/api/admin/payment-gateway")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Payment Gateway", description = "Payment gateway credentials (secrets are encrypted at rest)")
public class AdminPaymentGatewayController {

    private final PaymentGatewayConfigService service;

    @GetMapping("/{provider}")
    @Operation(summary = "Current config for a provider (e.g. razorpay); secrets are always masked. 404 if never configured")
    public ResponseEntity<ConfigResponse> get(@PathVariable String provider) {
        return service.get(provider)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{provider}")
    @Operation(summary = "Create or update a provider's credentials",
            description = "Leave keySecret/webhookSecret blank on an update to keep the stored value. "
                    + "keySecret is required the first time (422 GATEWAY_SECRET_REQUIRED).")
    public ConfigResponse update(@PathVariable String provider, @Valid @RequestBody UpdateRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser admin) {
        return service.update(provider, request, admin);
    }
}
