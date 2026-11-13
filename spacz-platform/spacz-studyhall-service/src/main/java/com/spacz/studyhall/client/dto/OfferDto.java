package com.spacz.studyhall.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A live offer from admin-service ({@code GET /internal/offers/live}).
 *
 * @param discountType   PERCENTAGE or FLAT
 * @param applicablePlan ANY, DAILY or MONTHLY
 */
public record OfferDto(Long id, String title, String discountType, BigDecimal discountValue, BigDecimal maxDiscount,
                       BigDecimal minBookingAmount, String applicablePlan, boolean firstBookingOnly, Instant startAt,
                       Instant endAt) {
}
