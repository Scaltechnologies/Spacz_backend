package com.spacz.studyhall.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Secrets shared by all SPACZ services. They are always supplied through environment variables
 * ({@code JWT_SECRET}, {@code INTERNAL_API_KEY}).
 */
@Validated
@ConfigurationProperties(prefix = "spacz.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Internal internal) {

    /**
     * @param secret HMAC-SHA256 key, at least 32 bytes
     * @param issuer expected {@code iss} claim
     */
    public record Jwt(@NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
                      @NotBlank String issuer) {
    }

    /**
     * @param apiKey shared key that service-to-service calls send in {@code X-Internal-Api-Key}
     */
    public record Internal(@NotBlank @Size(min = 16, message = "must be at least 16 characters") String apiKey) {
    }
}
