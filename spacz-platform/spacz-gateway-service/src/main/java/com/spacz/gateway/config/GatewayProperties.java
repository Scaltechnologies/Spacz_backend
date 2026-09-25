package com.spacz.gateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * @param jwtSecret          same HS256 key as the services (JWT_SECRET)
 * @param jwtIssuer          expected issuer
 * @param corsAllowedOrigins frontend origins allowed by CORS (CORS_ALLOWED_ORIGINS, comma separated)
 */
@Validated
@ConfigurationProperties(prefix = "spacz.gateway")
public record GatewayProperties(@NotBlank @Size(min = 32) String jwtSecret, @NotBlank String jwtIssuer,
                                @NotEmpty List<String> corsAllowedOrigins) {
}
