package com.spacz.studyhall.dto.seat;

import jakarta.validation.constraints.Min;

/**
 * A 1-based (row, column) cell of a block grid.
 */
public record GridPosition(@Min(1) int row, @Min(1) int column) {
}
