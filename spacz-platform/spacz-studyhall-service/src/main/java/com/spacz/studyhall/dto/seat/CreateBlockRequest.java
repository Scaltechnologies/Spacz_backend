package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Schema(description = """
        Creates a block (section) as a rows × columns grid and generates a seat in every cell except the gaps.
        Seats are numbered <prefix>-<row letter><column>: A1, A2 ... B1 (or G-A1 with prefix "G").""")
public record CreateBlockRequest(
        @Schema(example = "AC Reading Hall") @NotBlank @Size(max = 80) String name,
        @Schema(example = "5") @Min(1) @Max(60) int totalRows,
        @Schema(example = "10") @Min(1) @Max(60) int totalColumns,
        @Schema(example = "G", description = "Optional; needed when another block of the hall uses the same numbers")
        @Size(max = 8) @Pattern(regexp = "^[A-Za-z0-9]*$", message = "may contain letters and digits only")
        String seatNumberPrefix,
        @Schema(description = "Defaults to STANDARD") SeatType defaultSeatType,
        @Schema(description = "Cells with no seat (aisles, pillars)") @Size(max = 3600) List<@Valid @NotNull GridPosition> gaps,
        @Schema(example = "180.00", description = "Overrides the hall's daily price")
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal dailyPrice,
        @Schema(example = "3000.00", description = "Overrides the hall's monthly price")
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal monthlyPrice,
        @Schema(description = "Block-specific amenities (e.g. AC only in this block)") Set<@NotNull Long> amenityIds) {
}
