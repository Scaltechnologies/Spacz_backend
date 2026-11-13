package com.spacz.studyhall.dto.seat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.LayoutCellType;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.SeatType;

import java.math.BigDecimal;

/**
 * One grid position. Seat fields are present only for SEAT cells. Row 1 = row A is the entrance/user
 * side (bottom of the map); higher rows are further away. {@code position} is the row letter + column
 * (e.g. B12); {@code direction} is the facing / opening / airflow of directional cells.
 *
 * @param status    the seat's own status (AVAILABLE, RESERVED, MAINTENANCE, INACTIVE)
 * @param available bookable status and not booked/held for the requested dates
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutCellResponse(int row, int column, LayoutCellType type, String label, Long seatId,
                                 String seatNumber, SeatType seatType, SeatStatus status, Boolean available,
                                 BigDecimal pricePerDay, BigDecimal pricePerMonth, Direction direction,
                                 String position) {

    public static LayoutCellResponse empty(int row, int column) {
        return new LayoutCellResponse(row, column, LayoutCellType.EMPTY, null, null, null, null, null, null, null, null,
                null, com.spacz.studyhall.service.support.SeatNumbering.rowLabel(row) + column);
    }
}
