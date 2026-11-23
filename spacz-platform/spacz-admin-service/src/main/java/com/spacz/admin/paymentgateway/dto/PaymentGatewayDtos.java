package com.spacz.admin.paymentgateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.Instant;

/** Request/response shapes of the payment gateway settings API. */
public final class PaymentGatewayDtos {

    private PaymentGatewayDtos() {
    }

    /**
     * keySecret / webhookSecret are optional on an update to an existing config: leaving one blank
     * keeps the currently-stored (encrypted) value. The admin never sees the raw secret again after
     * saving it, so a "keep current value" affordance is required. keySecret is required the first
     * time a provider is configured.
     *
     * @param mode TEST or LIVE, defaults to TEST if omitted
     */
    public record UpdateRequest(@NotBlank(message = "keyId is required") @Size(max = 100) String keyId,
                                @Size(max = 200) String keySecret,
                                @Size(max = 200) String webhookSecret,
                                String mode,
                                Boolean isActive) {
    }

    /** Admin-facing view: secrets are always masked, never returned in recoverable form. */
    @Builder
    public record ConfigResponse(Long id, String provider, String mode, String keyId, String keySecretMasked,
                                 String webhookSecretMasked, boolean webhookSecretConfigured, boolean active,
                                 Long updatedBy, Instant updatedAt) {
    }

    /** Internal-only, decrypted: returned solely to studyhall-service over the private network. */
    @Builder
    public record ActiveCredentials(String provider, String mode, String keyId, String keySecret,
                                    String webhookSecret) {
    }
}
