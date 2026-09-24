package com.spacz.studyhall.mapper;

import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SeatMapper {

    private final StudyHallMapper hallMapper;

    public SeatResponse toResponse(Seat seat) {
        return toResponse(seat, null);
    }

    private SeatResponse toResponse(Seat seat, Boolean available) {
        return new SeatResponse(seat.getId(), seat.getBlock().getId(), seat.getSeatNumber(), seat.getRowIndex(),
                seat.getColumnIndex(), seat.getSeatType(), seat.getStatus(), seat.effectiveDailyPrice(),
                seat.effectiveMonthlyPrice(), available);
    }

    public BlockResponse toResponse(Block block) {
        return new BlockResponse(block.getId(), block.getName(), block.getTotalRows(), block.getTotalColumns(),
                block.getDisplayOrder(), block.getDailyPrice(), block.getMonthlyPrice(),
                hallMapper.amenities(block.getAmenities()),
                block.getSeats().stream().map(this::toResponse).toList());
    }

    /** Seat map for a date range: {@code available} = bookable status and not occupied. */
    public BlockResponse toResponse(Block block, Set<Long> occupiedSeatIds) {
        return new BlockResponse(block.getId(), block.getName(), block.getTotalRows(), block.getTotalColumns(),
                block.getDisplayOrder(), block.getDailyPrice(), block.getMonthlyPrice(),
                hallMapper.amenities(block.getAmenities()),
                block.getSeats().stream()
                        .map(seat -> toResponse(seat, seat.getStatus() == SeatStatus.AVAILABLE
                                && !occupiedSeatIds.contains(seat.getId())))
                        .toList());
    }
}
