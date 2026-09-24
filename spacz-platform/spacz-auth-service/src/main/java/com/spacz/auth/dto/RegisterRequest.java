package com.spacz.auth.dto;

import com.spacz.auth.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = """
        Email + password registration for students (role USER: firstName required) and vendors
        (role VENDOR: businessName, contactName and phone required). ADMIN cannot be registered.""")
public record RegisterRequest(
        @Schema(example = "USER") @NotNull Role role,
        @Schema(example = "asha@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "Secret123") @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD,
                message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @Schema(example = "Asha") @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Schema(example = "+919876543210") @Pattern(regexp = ValidationPatterns.PHONE,
                message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @Size(max = 80) String city,
        @Schema(example = "Focus Study Hall") @Size(max = 150) String businessName,
        @Size(max = 120) String contactName) {
}
