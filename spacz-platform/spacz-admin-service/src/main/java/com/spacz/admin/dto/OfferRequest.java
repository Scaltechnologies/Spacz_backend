package com.spacz.admin.dto;

import com.spacz.admin.entity.Offer.ApplicablePlan;
import com.spacz.admin.entity.Offer.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Create / fully replace an offer")
public record OfferRequest(
        @Schema(example = "Flat 50% off") @NotBlank @Size(max = 120) String title,
        @Schema(example = "Get 50% off on your first booking.") @Size(max = 1000) String description,
        @URL @Size(max = 500) String imageUrl,
        @NotNull DiscountType discountType,
        @Schema(example = "50", description = "Percent (1-100) for PERCENTAGE, rupees for FLAT")
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        @Schema(description = "Cap for PERCENTAGE discounts") @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)
        BigDecimal maxDiscount,
        @Schema(description = "Minimum booking amount before discount") @DecimalMin("0") @Digits(integer = 10, fraction = 2)
        BigDecimal minBookingAmount,
        @Schema(description = "Defaults to ANY") ApplicablePlan applicablePlan,
        @Schema(description = "Only for a student's first confirmed booking") Boolean firstBookingOnly,
        @Schema(example = "Valid on your first booking only.") @Size(max = 1000) String terms,
        @Schema(example = "2026-10-01T00:00:00Z") @NotNull Instant startAt,
        @Schema(example = "2027-03-27T18:29:59Z") @NotNull Instant endAt,
        @Schema(description = "Defaults to true") Boolean active,
        @Schema(description = "Higher shows first; defaults to 0") Integer priority) {

    @AssertTrue(message = "endAt must be after startAt")
    @Schema(hidden = true)
    public boolean isWindowValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }

    @AssertTrue(message = "a PERCENTAGE discount must be at most 100")
    @Schema(hidden = true)
    public boolean isPercentageValid() {
        return discountType != DiscountType.PERCENTAGE || discountValue == null
                || discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}
