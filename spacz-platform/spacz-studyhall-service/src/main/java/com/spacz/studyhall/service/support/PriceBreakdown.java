package com.spacz.studyhall.service.support;

import java.math.BigDecimal;

/**
 * Server-computed price of one booking.
 * {@code total = baseAmount - discountAmount + platformCharges + taxes}.
 *
 * @param baseAmount unitPrice × units
 * @param offerId    the applied admin offer, or null
 */
public record PriceBreakdown(BigDecimal unitPrice, long units, BigDecimal baseAmount, BigDecimal discountAmount,
                             Long offerId, String offerTitle, BigDecimal platformCharges, BigDecimal taxes,
                             BigDecimal total) {
}
