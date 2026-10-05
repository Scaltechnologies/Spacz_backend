package com.spacz.studyhall.dto.seat;

import java.math.BigDecimal;

/**
 * @param dailyPrice   effective price (block override or the hall default)
 * @param monthlyPrice effective price; null = monthly bookings not offered
 */
public record BlockLayoutResponse(Long id, String name, int displayOrder, BigDecimal dailyPrice,
                                  BigDecimal monthlyPrice, LayoutResponse layout) {
}
