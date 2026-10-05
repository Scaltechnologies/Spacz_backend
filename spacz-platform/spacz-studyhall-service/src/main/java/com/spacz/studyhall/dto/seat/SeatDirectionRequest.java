package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.Direction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/** The way the seat at (row, column) faces; null clears it. */
public record SeatDirectionRequest(
        @Schema(example = "1") @Min(1) int row,
        @Schema(example = "3") @Min(1) int column,
        @Schema(example = "NORTH", description = "NORTH, SOUTH, EAST or WEST") Direction direction) {
}
