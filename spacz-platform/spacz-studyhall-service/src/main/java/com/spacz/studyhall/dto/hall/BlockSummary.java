package com.spacz.studyhall.dto.hall;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param dailyPrice   effective price (block override or the hall default)
 * @param monthlyPrice effective price; null = monthly bookings not offered
 */
public record BlockSummary(Long id, String name, int totalRows, int totalColumns, int seatCount, BigDecimal dailyPrice,
                           BigDecimal monthlyPrice, List<String> amenities) {
}
