package com.spacz.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Study-hall vendor registration. The vendor starts in PENDING approval status.")
public record RegisterVendorRequest(
        @Schema(example = "owner@focushall.in") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "Secret123") @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD,
                message = ValidationPatterns.PASSWORD_MESSAGE) String password,
        @Schema(example = "Focus Study Hall") @NotBlank @Size(max = 150) String businessName,
        @Schema(example = "Ravi Kumar") @NotBlank @Size(max = 120) String contactName,
        @Schema(example = "+919812345678") @NotBlank @Pattern(regexp = ValidationPatterns.PHONE,
                message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @Schema(example = "Hyderabad") @Size(max = 80) String city) {
}
