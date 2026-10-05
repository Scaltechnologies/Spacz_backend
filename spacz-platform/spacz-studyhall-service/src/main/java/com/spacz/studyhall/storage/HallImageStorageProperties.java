package com.spacz.studyhall.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** spacz.storage.* — where uploaded hall photos live and how large they may be. */
@Validated
@ConfigurationProperties("spacz.storage")
public record HallImageStorageProperties(@NotBlank String hallImagesDir, @Min(1) long maxImageBytes) {
}
