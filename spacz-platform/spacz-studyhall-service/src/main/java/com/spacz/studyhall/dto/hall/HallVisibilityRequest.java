package com.spacz.studyhall.dto.hall;

import com.spacz.studyhall.entity.StudyHallStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record HallVisibilityRequest(
        @Schema(description = "ACTIVE (listed and bookable) or INACTIVE (hidden)", example = "INACTIVE")
        @NotNull StudyHallStatus status) {
}
