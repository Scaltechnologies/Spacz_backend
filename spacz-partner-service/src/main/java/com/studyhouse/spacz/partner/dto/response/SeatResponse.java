package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Seat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Seat with its block (and the block's property and owner). Same fields as the legacy API.")
public record SeatResponse(
        Long seatId,
        String seatNumber,
        BlockSummary block,
        double seatPrice,
        boolean reserved) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getSeatId(), seat.getSeatNumber(), BlockSummary.from(seat.getBlock()),
                seat.getSeatPrice(), seat.isReserved());
    }
}
