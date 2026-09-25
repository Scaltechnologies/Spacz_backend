package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateSeatRequest(
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "may contain letters, digits and '-' only") String seatNumber,
        @NotNull SeatType seatType,
        @Schema(description = "RESERVED = held by you offline; MAINTENANCE / INACTIVE = out of service. "
                + "None of them are bookable online.") @NotNull SeatStatus status,
        @Schema(description = "null = inherit from block / hall")
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerDay,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerMonth) {
}
