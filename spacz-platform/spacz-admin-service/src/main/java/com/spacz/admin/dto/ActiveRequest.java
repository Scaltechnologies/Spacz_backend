package com.spacz.admin.dto;

import jakarta.validation.constraints.NotNull;

/** Enable / disable (offers) or publish / unpublish (updates). */
public record ActiveRequest(@NotNull Boolean active) {
}
