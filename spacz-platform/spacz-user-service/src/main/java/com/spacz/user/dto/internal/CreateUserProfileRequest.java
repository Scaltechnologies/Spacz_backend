package com.spacz.user.dto.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Sent by auth-service at registration (email or phone-OTP).
 */
public record CreateUserProfileRequest(
        @NotNull @Positive Long userId,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 20) String phone,
        @Size(max = 80) String city) {
}
