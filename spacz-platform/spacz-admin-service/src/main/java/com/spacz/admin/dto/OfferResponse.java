package com.spacz.admin.dto;

import com.spacz.admin.entity.Offer;
import com.spacz.admin.entity.Offer.ApplicablePlan;
import com.spacz.admin.entity.Offer.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param live active, not archived and inside its validity window right now
 */
public record OfferResponse(Long id, String title, String description, String imageUrl, DiscountType discountType,
                            BigDecimal discountValue, BigDecimal maxDiscount, BigDecimal minBookingAmount,
                            ApplicablePlan applicablePlan, boolean firstBookingOnly, String terms, Instant startAt,
                            Instant endAt, boolean active, boolean live, int priority, Instant createdAt,
                            Instant updatedAt) {

    public static OfferResponse from(Offer offer, Instant now) {
        return new OfferResponse(offer.getId(), offer.getTitle(), offer.getDescription(), offer.getImageUrl(),
                offer.getDiscountType(), offer.getDiscountValue(), offer.getMaxDiscount(), offer.getMinBookingAmount(),
                offer.getApplicablePlan(), offer.isFirstBookingOnly(), offer.getTerms(), offer.getStartAt(),
                offer.getEndAt(), offer.isActive(), offer.isLive(now), offer.getPriority(), offer.getCreatedAt(),
                offer.getUpdatedAt());
    }
}
