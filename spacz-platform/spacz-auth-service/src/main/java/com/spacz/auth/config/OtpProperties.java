package com.spacz.auth.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param length                length of the numeric code
 * @param ttl                   how long a code is valid
 * @param maxAttempts           wrong guesses allowed per code
 * @param resendCooldown        minimum time between two codes for the same phone
 * @param maxPerHour            codes per phone per hour
 * @param defaultCountryCode    prefix for national numbers (e.g. +91 for a 10-digit Indian number)
 * @param smsProvider           "log" (development: writes the code to the log); plug in a real provider for production
 * @param exposeCodeInResponse  DEVELOPMENT ONLY: return the code in the API response (never enable in production)
 */
@Validated
@ConfigurationProperties(prefix = "spacz.otp")
public record OtpProperties(@Min(4) @Max(8) int length, @NotNull Duration ttl, @Min(1) int maxAttempts,
                            @NotNull Duration resendCooldown, @Min(1) int maxPerHour,
                            @NotBlank String defaultCountryCode, @NotBlank String smsProvider,
                            boolean exposeCodeInResponse) {
}
