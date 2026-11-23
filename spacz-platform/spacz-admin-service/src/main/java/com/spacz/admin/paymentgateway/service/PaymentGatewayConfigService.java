package com.spacz.admin.paymentgateway.service;

import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.exception.BusinessRuleException;
import com.spacz.admin.paymentgateway.dto.PaymentGatewayDtos.ActiveCredentials;
import com.spacz.admin.paymentgateway.dto.PaymentGatewayDtos.ConfigResponse;
import com.spacz.admin.paymentgateway.dto.PaymentGatewayDtos.UpdateRequest;
import com.spacz.admin.paymentgateway.entity.PaymentGatewayConfig;
import com.spacz.admin.paymentgateway.repository.PaymentGatewayConfigRepository;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentGatewayConfigService {

    private static final Set<String> MODES = Set.of("TEST", "LIVE");

    private final PaymentGatewayConfigRepository repository;
    private final SecretEncryptionService encryptionService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Optional<ConfigResponse> get(String provider) {
        return repository.findByProvider(normalize(provider)).map(this::toResponse);
    }

    @Transactional
    public ConfigResponse update(String provider, UpdateRequest request, AuthenticatedUser admin) {
        String normalizedProvider = normalize(provider);
        Optional<PaymentGatewayConfig> existing = repository.findByProvider(normalizedProvider);
        boolean rotateKeySecret = !isBlank(request.keySecret());
        boolean rotateWebhookSecret = !isBlank(request.webhookSecret());

        if (existing.isEmpty() && !rotateKeySecret) {
            throw new BusinessRuleException("GATEWAY_SECRET_REQUIRED",
                    "keySecret is required the first time " + normalizedProvider + " is configured");
        }
        String mode = isBlank(request.mode()) ? null : request.mode().trim().toUpperCase(Locale.ROOT);
        if (mode != null && !MODES.contains(mode)) {
            throw new BusinessRuleException("INVALID_GATEWAY_MODE", "mode must be one of " + MODES);
        }

        PaymentGatewayConfig config = existing.orElseGet(() -> PaymentGatewayConfig.builder()
                .provider(normalizedProvider)
                .build());

        String oldMode = config.getMode();
        boolean wasActive = existing.map(PaymentGatewayConfig::getIsActive).orElse(false);

        config.setKeyId(request.keyId().trim());
        if (mode != null) {
            config.setMode(mode);
        } else if (config.getMode() == null) {
            config.setMode("TEST");
        }
        if (rotateKeySecret) {
            config.setKeySecretEncrypted(encryptionService.encrypt(request.keySecret().trim()));
        }
        if (rotateWebhookSecret) {
            config.setWebhookSecretEncrypted(encryptionService.encrypt(request.webhookSecret().trim()));
        }
        config.setIsActive(request.isActive() != null ? request.isActive() : true);
        config.setUpdatedBy(admin != null ? admin.userId() : null);

        // saveAndFlush (not save): @UpdateTimestamp only stamps updatedAt at flush time, and the
        // response built below must reflect that fresh value immediately, not the pre-flush one.
        PaymentGatewayConfig saved = repository.saveAndFlush(config);

        // Never write secret values into the audit trail, only metadata about what changed.
        auditService.record(admin, AuditAction.PAYMENT_GATEWAY_CONFIG_UPDATED, "PAYMENT_GATEWAY_CONFIG", saved.getId(),
                normalizedProvider + " keyId=" + saved.getKeyId()
                        + " mode " + (existing.isPresent() ? oldMode : "-") + " -> " + saved.getMode()
                        + ", active " + wasActive + " -> " + saved.getIsActive()
                        + (rotateKeySecret ? ", keySecret rotated" : "")
                        + (rotateWebhookSecret ? ", webhookSecret rotated" : ""));

        return toResponse(saved);
    }

    /** Internal-only: decrypted credentials for the active config of a provider. Empty if unconfigured/inactive. */
    @Transactional(readOnly = true)
    public Optional<ActiveCredentials> getActiveCredentials(String provider) {
        return repository.findByProvider(normalize(provider))
                .filter(PaymentGatewayConfig::getIsActive)
                .map(c -> ActiveCredentials.builder()
                        .provider(c.getProvider())
                        .mode(c.getMode())
                        .keyId(c.getKeyId())
                        .keySecret(encryptionService.decrypt(c.getKeySecretEncrypted()))
                        .webhookSecret(encryptionService.decrypt(c.getWebhookSecretEncrypted()))
                        .build());
    }

    private ConfigResponse toResponse(PaymentGatewayConfig config) {
        return ConfigResponse.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .mode(config.getMode())
                .keyId(config.getKeyId())
                .keySecretMasked(encryptionService.mask(encryptionService.decrypt(config.getKeySecretEncrypted())))
                .webhookSecretMasked(encryptionService.mask(encryptionService.decrypt(config.getWebhookSecretEncrypted())))
                .webhookSecretConfigured(config.getWebhookSecretEncrypted() != null)
                .active(Boolean.TRUE.equals(config.getIsActive()))
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private static String normalize(String provider) {
        return provider.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
