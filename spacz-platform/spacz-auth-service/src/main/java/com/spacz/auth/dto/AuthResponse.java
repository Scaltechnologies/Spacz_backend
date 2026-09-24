package com.spacz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(description = "JWT access token; send as Authorization: Bearer <token>") String accessToken,
        @Schema(description = "Opaque refresh token; single use, rotated on every refresh") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresIn,
        AccountResponse user) {
}
