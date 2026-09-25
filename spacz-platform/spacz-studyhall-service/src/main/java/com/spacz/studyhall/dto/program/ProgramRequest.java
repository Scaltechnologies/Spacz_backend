package com.spacz.studyhall.dto.program;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProgramRequest(
        @Schema(example = "EAMCET") @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '_' and '-' only") String code,
        @Schema(example = "TS / AP EAMCET") @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Schema(example = "Engineering") @NotBlank @Size(max = 60) String category,
        @Schema(description = "Defaults to true on create") Boolean active) {
}
