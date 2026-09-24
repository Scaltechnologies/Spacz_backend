package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.dto.hall.AmenityResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * A grid. Cells of the rows × columns grid that have no seat in {@code seats} are gaps.
 *
 * @param dailyPrice   the block's own override (null = inherits the hall price)
 * @param monthlyPrice the block's own override (null = inherits the hall price)
 */
public record BlockResponse(Long id, String name, int totalRows, int totalColumns, int displayOrder,
                            BigDecimal dailyPrice, BigDecimal monthlyPrice, List<AmenityResponse> amenities,
                            List<SeatResponse> seats) {
}
