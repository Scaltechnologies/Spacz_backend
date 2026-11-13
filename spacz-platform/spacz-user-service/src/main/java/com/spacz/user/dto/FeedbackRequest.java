package com.spacz.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(@Schema(example = "5") @NotNull @Min(1) @Max(5) Integer rating,
                              @Schema(example = "Booking was quick and easy.") @Size(max = 1000) String comment) {
}
