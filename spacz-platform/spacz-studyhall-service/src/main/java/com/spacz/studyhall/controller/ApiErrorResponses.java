package com.spacz.studyhall.controller;

import com.spacz.studyhall.exception.ApiError;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Common error responses of authenticated endpoints, for the OpenAPI document.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Wrong role or vendor suspended", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "404", description = "Not found (or not yours)", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "409", description = "Conflict (duplicate, seat taken, in use)", content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "422", description = "Business rule violation", content = @Content(schema = @Schema(implementation = ApiError.class)))
public @interface ApiErrorResponses {
}
