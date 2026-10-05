package com.spacz.studyhall.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Charges added to every booking on top of the seat price (after any offer discount).
 *
 * @param platformFee flat platform charge per booking (INR)
 * @param taxPercent  tax rate applied to (seat price - discount + platform fee)
 */
@Validated
@ConfigurationProperties(prefix = "spacz.pricing")
public record PricingProperties(@NotNull @DecimalMin("0") @DefaultValue("0") BigDecimal platformFee,
                                @NotNull @DecimalMin("0") @DecimalMax("100") @DefaultValue("0") BigDecimal taxPercent) {
}
