package com.spacz.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminProfileRequest(
        @Schema(example = "Priya Sharma") @NotBlank @Size(max = 120) String fullName,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "^\\+?[0-9][0-9 -]{6,18}$", message = "must be a valid phone number") String phone,
        @Schema(example = "Operations Manager") @Size(max = 80) String title) {
}
