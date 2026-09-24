package com.spacz.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "spacz.auth")
public record AuthProperties(
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Min(1) int maxFailedLogins,
        @NotNull Duration lockDuration,
        @Valid @NotNull BootstrapAdmin bootstrapAdmin) {

    /**
     * The first ADMIN account, created at startup when both values are set and the email does not exist yet.
     * Never committed to source control: supplied via BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD.
     */
    public record BootstrapAdmin(String email, String password) {
    }
}
