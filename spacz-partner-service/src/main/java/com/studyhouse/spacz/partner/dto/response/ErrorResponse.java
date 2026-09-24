package com.studyhouse.spacz.partner.dto.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error body returned for every non-2xx response")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        @Schema(example = "2026-09-24T10:15:30Z") Instant timestamp,
        @Schema(example = "404") int status,
        @Schema(example = "NOT_FOUND") String error,
        @Schema(example = "Property not found with ID: 3") String message,
        @Schema(example = "/api/properties/3") String path,
        @Schema(description = "Per-field validation errors (400 only)") List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }
}
