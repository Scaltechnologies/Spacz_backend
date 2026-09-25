package com.spacz.studyhall.dto.vendor;

import com.spacz.studyhall.dto.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Vendor / institute business profile. Replaces all editable fields.")
public record VendorProfileRequest(
        @Schema(example = "Focus Study Hall") @NotBlank @Size(max = 150) String businessName,
        @Schema(example = "Ravi Kumar") @Size(max = 120) String contactName,
        @Schema(example = "+919812345678") @Pattern(regexp = ValidationPatterns.PHONE,
                message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @Schema(example = "owner@focushall.in") @Email @Size(max = 254) String email,
        @Size(max = 255) String addressLine,
        @Size(max = 80) String city,
        @Size(max = 80) String state,
        @Pattern(regexp = ValidationPatterns.PINCODE, message = ValidationPatterns.PINCODE_MESSAGE) String pincode,
        @Schema(example = "36AABCU9603R1ZM") @Size(max = 20) String gstNumber,
        @Size(max = 2000) String description,
        @URL @Size(max = 500) String logoUrl) {
}
