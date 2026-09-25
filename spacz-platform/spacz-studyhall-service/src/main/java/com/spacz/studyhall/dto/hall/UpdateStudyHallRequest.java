package com.spacz.studyhall.dto.hall;

import com.spacz.studyhall.dto.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Full replacement of a study hall's details. Price changes apply to new bookings only.")
public record UpdateStudyHallRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @Pattern(regexp = ValidationPatterns.PINCODE, message = ValidationPatterns.PINCODE_MESSAGE) String pincode,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerDay,
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerMonth,
        @Size(max = 4000) String rules) {

    @AssertTrue(message = "latitude and longitude must be provided together")
    @Schema(hidden = true)
    public boolean isLocationComplete() {
        return (latitude == null) == (longitude == null);
    }
}
