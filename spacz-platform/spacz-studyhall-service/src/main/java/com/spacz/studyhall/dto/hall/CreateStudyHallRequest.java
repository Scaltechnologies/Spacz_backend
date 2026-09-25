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
import java.time.LocalTime;

@Schema(description = "Creates a DRAFT study hall. openingTime/closingTime seed the same hours for all seven days; "
        + "use PUT /operating-hours for per-day hours. Prices are defaults that blocks and seats can override.")
public record CreateStudyHallRequest(
        @Schema(example = "Focus Hall - Ameerpet") @NotBlank @Size(max = 150) String name,
        @Size(max = 4000) String description,
        @Schema(example = "Plot 12, Main Road, Ameerpet") @NotBlank @Size(max = 255) String addressLine,
        @Schema(example = "Hyderabad") @NotBlank @Size(max = 80) String city,
        @Schema(example = "Telangana") @NotBlank @Size(max = 80) String state,
        @Schema(example = "500016") @Pattern(regexp = ValidationPatterns.PINCODE,
                message = ValidationPatterns.PINCODE_MESSAGE) String pincode,
        @Schema(example = "17.4375") @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @Schema(example = "78.4482") @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Schema(example = "+919812345678") @Pattern(regexp = ValidationPatterns.PHONE,
                message = ValidationPatterns.PHONE_MESSAGE) String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        @Schema(example = "150.00") @NotNull @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 8, fraction = 2) BigDecimal pricePerDay,
        @Schema(example = "2500.00", description = "Optional; enables MONTHLY bookings")
        @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal pricePerMonth,
        @Size(max = 4000) String rules,
        @Schema(example = "06:00") @NotNull LocalTime openingTime,
        @Schema(example = "22:00") @NotNull LocalTime closingTime) {

    @AssertTrue(message = "closingTime must be after openingTime")
    @Schema(hidden = true)
    public boolean isHoursValid() {
        return openingTime == null || closingTime == null || closingTime.isAfter(openingTime);
    }

    @AssertTrue(message = "latitude and longitude must be provided together")
    @Schema(hidden = true)
    public boolean isLocationComplete() {
        return (latitude == null) == (longitude == null);
    }
}
