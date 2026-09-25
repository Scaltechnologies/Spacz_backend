package com.spacz.studyhall.dto;

import com.spacz.studyhall.entity.StatusAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusActionRequest(
        @NotNull StatusAction action,
        @Schema(description = "Required for REJECT and SUSPEND") @Size(max = 500) String reason) {
}
