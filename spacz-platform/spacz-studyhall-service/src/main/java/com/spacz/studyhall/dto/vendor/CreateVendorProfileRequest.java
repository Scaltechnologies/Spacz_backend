package com.spacz.studyhall.dto.vendor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Sent by auth-service at vendor registration. Phone-OTP registrations send only vendorId + phone;
 * the vendor completes the profile later.
 */
public record CreateVendorProfileRequest(
        @NotNull @Positive Long vendorId,
        @Email @Size(max = 254) String email,
        @Size(max = 150) String businessName,
        @Size(max = 120) String contactName,
        @Size(max = 20) String phone,
        @Size(max = 80) String city) {
}
