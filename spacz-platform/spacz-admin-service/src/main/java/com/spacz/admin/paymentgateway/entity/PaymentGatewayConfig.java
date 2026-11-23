package com.spacz.admin.paymentgateway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Admin-entered payment gateway credentials: lets an admin paste/rotate Razorpay test or live
 * keys from the dashboard, with no code change or redeploy needed. keySecret/webhookSecret are
 * stored via {@link com.spacz.admin.paymentgateway.service.SecretEncryptionService}, never
 * plaintext; keyId is not secret (Razorpay's own checkout JS embeds it client-side) and is stored
 * as-is. One row per provider ("RAZORPAY" today, extensible if another gateway is added later).
 */
@Entity
@Table(name = "payment_gateway_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_pgc_provider", columnNames = "provider"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentGatewayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String provider;

    /** TEST or LIVE: informational; which key prefix is actually used is whatever keyId is. */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String mode = "TEST";

    @Column(name = "key_id", nullable = false, length = 100)
    private String keyId;

    @Column(name = "key_secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String keySecretEncrypted;

    @Column(name = "webhook_secret_encrypted", columnDefinition = "TEXT")
    private String webhookSecretEncrypted;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
