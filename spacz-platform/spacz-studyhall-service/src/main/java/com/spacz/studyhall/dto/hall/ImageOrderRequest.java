package com.spacz.studyhall.dto.hall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Every photo id of the hall, exactly once, in the order to show them")
public record ImageOrderRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> imageIds) {
}
