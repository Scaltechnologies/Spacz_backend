package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.FixturePosition;
import com.spacz.studyhall.entity.FixtureType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A fixture on a block edge / centre; it occupies no cell (never a seat). */
public record FixtureRequest(
        @Schema(example = "AC") @NotNull FixtureType type,
        @Schema(example = "NORTH", description = "NORTH (far edge), SOUTH (entrance edge), EAST, WEST or CENTER") @NotNull FixturePosition position,
        @Schema(example = "3", description = "Column (NORTH/SOUTH) or row (EAST/WEST) along the edge; null = centred") Integer offset,
        @Schema(example = "SOUTH", description = "Airflow; CENTER = centralized") Direction direction,
        @Schema(example = "Split AC") @Size(max = 40) String label) {
}
