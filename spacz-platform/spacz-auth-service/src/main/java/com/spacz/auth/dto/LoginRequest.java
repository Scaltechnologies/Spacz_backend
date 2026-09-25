package com.spacz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Password login. `email` may also be the account's phone number.")
public record LoginRequest(
        @Schema(example = "asha@example.com") @NotBlank @Size(max = 254) String email,
        @Schema(example = "Secret123") @NotBlank @Size(max = 72) String password) {
}
