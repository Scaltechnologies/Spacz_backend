package com.spacz.studyhall.mapper;

import com.spacz.studyhall.dto.seat.BlockLayoutResponse;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.FloorResponse;
import com.spacz.studyhall.dto.seat.LayoutCellResponse;
import com.spacz.studyhall.dto.seat.LayoutResponse;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.BlockLayout;
import com.spacz.studyhall.entity.Floor;
import com.spacz.studyhall.entity.LayoutCell;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
        return new BlockResponse(block.getId(), block.getFloor().getId(), block.getName(), block.getTotalRows(),
                block.getTotalColumns(), block.getDisplayOrder(), block.getDailyPrice(), block.getMonthlyPrice(),
                hallMapper.amenities(block.getAmenities()),
                block.getSeats().stream().map(this::toResponse).toList());
    }

    /** Seat map for a date range: {@code available} = bookable status and not occupied. */
    public BlockResponse toResponse(Block block, Set<Long> occupiedSeatIds) {
        return new BlockResponse(block.getId(), block.getFloor().getId(), block.getName(), block.getTotalRows(),
                block.getTotalColumns(), block.getDisplayOrder(), block.getDailyPrice(), block.getMonthlyPrice(),
                hallMapper.amenities(block.getAmenities()),
                block.getSeats().stream()
                        .map(seat -> toResponse(seat, isAvailable(seat, occupiedSeatIds)))
                        .toList());
    }

    public FloorResponse toResponse(Floor floor) {
        return new FloorResponse(floor.getId(), floor.getFloorNumber(), floor.getName(), floor.getDescription(),
                floor.getDisplayOrder());
    }

    /**
     * The full rows × columns grid in row-major order; positions without a cell are EMPTY.
     * With {@code occupiedSeatIds} null, seat availability is not evaluated ({@code available} omitted).
     */
    public BlockLayoutResponse toLayoutResponse(Block block, BlockLayout layout, Set<Long> occupiedSeatIds) {
        int rows = layout.getRows();
        int columns = layout.getColumns();
        LayoutCell[] grid = new LayoutCell[rows * columns];
        for (LayoutCell cell : layout.getCells()) {
            if (layout.contains(cell.getRowIndex(), cell.getColumnIndex())) {
                grid[(cell.getRowIndex() - 1) * columns + cell.getColumnIndex() - 1] = cell;
            }
        }
        List<LayoutCellResponse> cells = new ArrayList<>(grid.length);
        for (int i = 0; i < grid.length; i++) {
            int row = i / columns + 1;
            int column = i % columns + 1;
            cells.add(grid[i] == null ? LayoutCellResponse.empty(row, column) : toResponse(grid[i], occupiedSeatIds));
        }
        return new BlockLayoutResponse(block.getId(), block.getName(), block.getDisplayOrder(),
                block.effectiveDailyPrice(), block.effectiveMonthlyPrice(),
                new LayoutResponse(layout.getLayoutType(), rows, columns, layout.getLayoutVersion(), cells,
                        layout.getFixtures().stream()
                                .map(f -> new com.spacz.studyhall.dto.seat.FixtureResponse(f.getId(), f.getType(),
                                        f.getPosition(), f.getOffset(), f.getDirection(), f.getLabel()))
                                .toList()));
    }

    private static LayoutCellResponse toResponse(LayoutCell cell, Set<Long> occupiedSeatIds) {
        Seat seat = cell.getSeat();
        String position = com.spacz.studyhall.service.support.SeatNumbering.rowLabel(cell.getRowIndex())
                + cell.getColumnIndex();
        if (seat == null) {
            return new LayoutCellResponse(cell.getRowIndex(), cell.getColumnIndex(), cell.getType(), cell.getLabel(),
                    null, null, null, null, null, null, null, cell.getDirection(), position);
        }
        return new LayoutCellResponse(cell.getRowIndex(), cell.getColumnIndex(), cell.getType(), cell.getLabel(),
                seat.getId(), seat.getSeatNumber(), seat.getSeatType(), seat.getStatus(),
                occupiedSeatIds == null ? null : isAvailable(seat, occupiedSeatIds),
                seat.effectiveDailyPrice(), seat.effectiveMonthlyPrice(), cell.getDirection(), position);
    }

    private static boolean isAvailable(Seat seat, Set<Long> occupiedSeatIds) {
        return seat.getStatus() == SeatStatus.AVAILABLE && !occupiedSeatIds.contains(seat.getId());
    }
}
