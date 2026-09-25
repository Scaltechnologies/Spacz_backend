package com.spacz.studyhall.dto.hall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AmenityRequest(
        @Schema(example = "WIFI") @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '_' and '-' only") String code,
        @Schema(example = "Wi-Fi") @NotBlank @Size(max = 80) String name,
        @Schema(example = "wifi") @Size(max = 60) String icon,
        @Size(max = 300) String description,
        @Schema(description = "Defaults to true on create") Boolean active) {
}
