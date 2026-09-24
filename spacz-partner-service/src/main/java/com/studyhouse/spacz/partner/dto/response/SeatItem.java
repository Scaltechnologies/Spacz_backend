package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Seat;

/** A seat inside its block's {@code seats} list (no back-reference to the block). */
public record SeatItem(
        Long seatId,
        String seatNumber,
        double seatPrice,
        boolean reserved) {

    public static SeatItem from(Seat seat) {
        return new SeatItem(seat.getSeatId(), seat.getSeatNumber(), seat.getSeatPrice(), seat.isReserved());
    }
}
