package com.spacz.studyhall.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param approvalRequired when false, a submitted study hall is approved automatically
 * @param maxImages        maximum images per study hall
 * @param maxLayoutCells   maximum rows × columns of one seat layout
 */
@Validated
@ConfigurationProperties(prefix = "spacz.studyhall")
public record StudyHallProperties(boolean approvalRequired, @Min(1) int maxImages, @Min(1) int maxLayoutCells) {
}
