package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.LayoutCellType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A non-seat cell (walkway, entrance, table ...). Seats are managed with the seat endpoints. */
public record LayoutFeatureRequest(
        @Schema(example = "1") @Min(1) int row,
        @Schema(example = "8") @Min(1) int column,
        @Schema(example = "ENTRANCE", description = "Any type except SEAT and EMPTY") @NotNull LayoutCellType type,
        @Schema(example = "Entrance") @Size(max = 40) String label,
        @Schema(example = "SOUTH", description = "Only for ENTRANCE, EXIT, AC, STAIRS, LIFT; CENTER only for AC")
        Direction direction) {
}
