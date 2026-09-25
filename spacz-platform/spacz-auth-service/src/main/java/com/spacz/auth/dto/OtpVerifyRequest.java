package com.spacz.auth.dto;

import com.spacz.auth.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = """
        Verifies the code and logs in. If no account exists for the phone, the call answers
        422 REGISTRATION_REQUIRED (the code stays valid); repeat it with `role` (USER or VENDOR) and,
        for USER, `firstName` to register.""")
public record OtpVerifyRequest(
        @Schema(example = "9876543210") @NotBlank @Size(max = 20) String phone,
        @Schema(example = "482913") @NotBlank @Pattern(regexp = "^\\d{4,8}$", message = "must be the numeric code") String code,
        @Schema(description = "Only for registration") Role role,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 80) String city,
        @Size(max = 150) String businessName,
        @Size(max = 120) String contactName) {
}
