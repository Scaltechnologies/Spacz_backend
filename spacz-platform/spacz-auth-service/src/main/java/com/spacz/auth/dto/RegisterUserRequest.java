package com.spacz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Student / aspirant registration")
public record RegisterUserRequest(
        @Schema(example = "asha@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "Secret123") @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD,
                message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @Schema(example = "Asha") @NotBlank @Size(max = 80) String firstName,
        @Schema(example = "Rao") @Size(max = 80) String lastName,
        @Schema(example = "+919876543210") @Pattern(regexp = ValidationPatterns.PHONE,
                message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @Schema(example = "Hyderabad") @Size(max = 80) String city) {
}
