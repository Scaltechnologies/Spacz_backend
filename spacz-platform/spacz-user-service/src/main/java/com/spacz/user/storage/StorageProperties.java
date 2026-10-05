package com.spacz.user.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param documentsDir  directory for user documents (a persistent volume in Docker)
 * @param maxFileBytes  largest accepted upload; must stay below the gateway's 2 MB request limit
 */
@Validated
@ConfigurationProperties(prefix = "spacz.storage")
public record StorageProperties(@NotBlank String documentsDir, @Min(1) long maxFileBytes) {
}
