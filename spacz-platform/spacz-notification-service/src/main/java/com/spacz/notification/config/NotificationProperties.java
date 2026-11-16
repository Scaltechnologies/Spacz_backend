package com.spacz.notification.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param retention         in-app notifications older than this are deleted
 * @param deviceTokenTtl    device tokens not refreshed for this long are deleted
 * @param maxDevicesPerUser devices kept per account (least recently seen are dropped)
 * @param cleanupCron       when the cleanup runs (server time)
 */
@Validated
@ConfigurationProperties(prefix = "spacz.notifications")
public record NotificationProperties(@NotNull Duration retention,
                                     @NotNull Duration deviceTokenTtl,
                                     @Min(1) int maxDevicesPerUser,
                                     @NotBlank String cleanupCron) {
}
