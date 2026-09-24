package com.spacz.studyhall.dto.hall;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AmenityIdsRequest(@NotEmpty @Size(max = 50) Set<@NotNull Long> amenityIds) {
}
