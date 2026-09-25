package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateSeatRequest(
        @Schema(example = "3") @NotNull Long blockId,
        @Schema(example = "C11") @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "may contain letters, digits and '-' only") String seatNumber,
        @Min(1) int row,
        @Min(1) int column,
        @NotNull SeatType seatType,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerDay,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerMonth) {
}
