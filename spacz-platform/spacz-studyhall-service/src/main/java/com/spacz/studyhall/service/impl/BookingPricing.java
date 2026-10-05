package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.client.AdminServiceClient;
import com.spacz.studyhall.client.dto.OfferDto;
import com.spacz.studyhall.config.PricingProperties;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.service.support.PriceBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The single place booking prices are computed (quote and create use the same code):
 * seat price × units, minus the best applicable live admin offer, plus platform fee and tax.
 * If admin-service is unreachable the booking is priced without an offer rather than failing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AdminServiceClient adminServiceClient;
    private final BookingRepository bookingRepository;
    private final PricingProperties properties;

    public PriceBreakdown price(Long userId, BookingPlan plan, LocalDate startDate, LocalDate endDate,
                                BigDecimal unitPrice, Instant now) {
        long units = Booking.units(plan, startDate, endDate);
        BigDecimal base = money(unitPrice.multiply(BigDecimal.valueOf(units)));

        OfferDto bestOffer = null;
        BigDecimal discount = BigDecimal.ZERO;
        Boolean hasPriorBooking = null;
        for (OfferDto offer : liveOffers()) {
            if (!appliesTo(offer, plan, base, now)) {
                continue;
            }
            if (offer.firstBookingOnly()) {
                if (hasPriorBooking == null) {
                    hasPriorBooking = bookingRepository.existsByUserIdAndStatusIn(userId,
                            List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED));
                }
                if (hasPriorBooking) {
                    continue;
                }
            }
            BigDecimal candidate = discountFor(offer, base);
            if (candidate.compareTo(discount) > 0) {
                discount = candidate;
                bestOffer = offer;
            }
        }

        BigDecimal platformFee = money(properties.platformFee());
        BigDecimal taxable = base.subtract(discount).add(platformFee);
        BigDecimal taxes = money(taxable.multiply(properties.taxPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal total = money(taxable.add(taxes));
        return new PriceBreakdown(unitPrice, units, base, discount, bestOffer == null ? null : bestOffer.id(),
                bestOffer == null ? null : bestOffer.title(), platformFee, taxes, total);
    }

    private List<OfferDto> liveOffers() {
        try {
            return adminServiceClient.liveOffers();
        } catch (SpaczException ex) {
            log.warn("Live offers unavailable, pricing without discount: {}", ex.getMessage());
            return List.of();
        }
    }

    private static boolean appliesTo(OfferDto offer, BookingPlan plan, BigDecimal base, Instant now) {
        if (offer.startAt() != null && now.isBefore(offer.startAt())) {
            return false;
        }
        if (offer.endAt() != null && !now.isBefore(offer.endAt())) {
            return false;
        }
        if (offer.applicablePlan() != null && !"ANY".equals(offer.applicablePlan())
                && !plan.name().equals(offer.applicablePlan())) {
            return false;
        }
        return offer.minBookingAmount() == null || base.compareTo(offer.minBookingAmount()) >= 0;
    }

    private static BigDecimal discountFor(OfferDto offer, BigDecimal base) {
        if (offer.discountValue() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal discount = "PERCENTAGE".equals(offer.discountType())
                ? base.multiply(offer.discountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : offer.discountValue();
        if (offer.maxDiscount() != null && discount.compareTo(offer.maxDiscount()) > 0) {
            discount = offer.maxDiscount();
        }
        return money(discount.min(base));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
