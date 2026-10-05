package com.spacz.studyhall.dto.seat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Moves an existing seat of the block to another cell; its id, number and bookings are kept. */
public record SeatMoveRequest(@Schema(example = "42") @NotNull Long seatId,
                              @Schema(example = "3") @Min(1) int row,
                              @Schema(example = "5") @Min(1) int column) {
}
