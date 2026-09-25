package com.spacz.studyhall.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by every SPACZ service.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Standard error response")
public record ApiError(
        @Schema(example = "2026-09-25T10:15:30Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "VALIDATION_ERROR") String error,
        @Schema(example = "Request validation failed") String message,
        @Schema(example = "/api/auth/register/user") String path,
        @Schema(example = "6f1c2a0e-6f7b-4a43-9a51-1c7f3b2c9d11") String correlationId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
