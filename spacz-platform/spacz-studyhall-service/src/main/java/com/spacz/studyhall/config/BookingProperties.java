package com.spacz.studyhall.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

/**
 * @param holdDuration   how long a PENDING booking holds the seat before it expires
 * @param autoConfirm    confirm immediately (no payment step) instead of creating a PENDING hold
 * @param maxDays        maximum length of one booking in days
 * @param maxAdvanceDays how far ahead a booking may start
 * @param zone           business time zone used for "today"
 */
@Validated
@ConfigurationProperties(prefix = "spacz.booking")
public record BookingProperties(@NotNull Duration holdDuration, boolean autoConfirm, @Min(1) int maxDays,
                                @Min(0) int maxAdvanceDays, @NotNull ZoneId zone) {
}
