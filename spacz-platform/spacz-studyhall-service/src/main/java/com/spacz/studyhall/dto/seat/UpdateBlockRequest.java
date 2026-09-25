package com.spacz.studyhall.dto.seat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

@Schema(description = "Replaces a block's name, prices and amenities. The grid can grow; it can shrink only "
        + "if no seat sits outside the new size.")
public record UpdateBlockRequest(
        @NotBlank @Size(max = 80) String name,
        @Min(1) @Max(60) int totalRows,
        @Min(1) @Max(60) int totalColumns,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal dailyPrice,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal monthlyPrice,
        Set<@NotNull Long> amenityIds) {
}
