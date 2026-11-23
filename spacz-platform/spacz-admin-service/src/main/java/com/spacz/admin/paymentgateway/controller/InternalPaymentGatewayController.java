package com.spacz.admin.paymentgateway.controller;

import com.spacz.admin.paymentgateway.dto.PaymentGatewayDtos.ActiveCredentials;
import com.spacz.admin.paymentgateway.service.PaymentGatewayConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by studyhall-service only (internal API key; the gateway denies /internal/** from outside)
 * to fetch decrypted credentials. Never exposed publicly.
 */
@RestController
@RequestMapping("/internal/payment-gateway")
@RequiredArgsConstructor
@Tag(name = "Internal - Payment Gateway", description = "Decrypted gateway credentials for studyhall-service")
@SecurityRequirement(name = "internalApiKey")
public class InternalPaymentGatewayController {

    private final PaymentGatewayConfigService service;

    @GetMapping("/{provider}/active")
    @Operation(summary = "Decrypted credentials of the active config; 404 when unconfigured or inactive")
    public ResponseEntity<ActiveCredentials> getActive(@PathVariable String provider) {
        return service.getActiveCredentials(provider)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
