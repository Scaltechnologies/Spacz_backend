package com.spacz.studyhall.dto.seat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FloorRequest(
        @Schema(example = "1", description = "Unique per hall: 0 = ground, negative = basement")
        @NotNull @Min(-5) @Max(200) Integer floorNumber,
        @Schema(example = "First Floor") @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @Schema(description = "Defaults to after the existing floors") @Min(0) @Max(1000) Integer displayOrder) {
}
