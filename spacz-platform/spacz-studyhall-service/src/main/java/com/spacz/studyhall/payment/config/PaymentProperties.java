package com.spacz.studyhall.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param sessionTtl online checkout session; past this the reconciliation sweep resolves or expires it
 * @param holdGrace  the booking's seat hold is extended to payment expiresAt + this
 * @param payAtHall  Pay-at-Hall session settings
 */
@Validated
@ConfigurationProperties(prefix = "spacz.payment")
public record PaymentProperties(@NotNull Duration sessionTtl, @NotNull Duration holdGrace,
                                @Valid @NotNull PayAtHall payAtHall) {

    /**
     * @param sessionTtl      how long a QR / code stays valid (also capped at the end of the booking's start day)
     * @param maxCodeAttempts wrong-code attempts before code entry is locked
     * @param lockout         how long code entry stays locked
     */
    public record PayAtHall(@NotNull Duration sessionTtl, @Min(1) int maxCodeAttempts, @NotNull Duration lockout) {
    }
}
