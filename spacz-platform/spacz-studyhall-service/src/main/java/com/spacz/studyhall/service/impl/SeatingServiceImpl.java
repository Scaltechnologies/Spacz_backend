package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.config.StudyHallProperties;
import com.spacz.studyhall.dto.seat.BlockLayoutResponse;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.CreateSeatRequest;
import com.spacz.studyhall.dto.seat.SeatMoveRequest;
import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.LayoutFixture;

import com.spacz.studyhall.dto.seat.FloorRequest;
import com.spacz.studyhall.dto.seat.FloorResponse;
import com.spacz.studyhall.dto.seat.GridPosition;
import com.spacz.studyhall.dto.seat.LayoutFeatureRequest;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.dto.seat.UpdateBlockRequest;
import com.spacz.studyhall.dto.seat.UpdateLayoutRequest;
import com.spacz.studyhall.dto.seat.UpdateSeatRequest;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.BlockLayout;
import com.spacz.studyhall.entity.EnrollmentStatus;
import com.spacz.studyhall.entity.Floor;
import com.spacz.studyhall.entity.LayoutCellType;
import com.spacz.studyhall.entity.LayoutType;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.SeatType;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.exception.DuplicateResourceException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.mapper.SeatMapper;
import com.spacz.studyhall.repository.AmenityRepository;
import com.spacz.studyhall.repository.BlockLayoutRepository;
import com.spacz.studyhall.repository.BlockRepository;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.EnrollmentRepository;
import com.spacz.studyhall.repository.FloorRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.support.BlockLayouts;
import com.spacz.studyhall.service.support.LayoutTemplateParser;
import com.spacz.studyhall.service.support.SeatNumbering;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeatingServiceImpl implements SeatingService {

    private final VendorAccess vendorAccess;
    private final FloorRepository floorRepository;
    private final BlockRepository blockRepository;
    private final BlockLayoutRepository layoutRepository;
    private final BlockLayouts blockLayouts;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AmenityRepository amenityRepository;
    private final SeatMapper seatMapper;
    private final StudyHallProperties properties;

    // ------------------------------------------------------------------ floors

    @Override
    @Transactional(readOnly = true)
    public List<FloorResponse> listFloors(Long vendorId, Long studyHallId) {
        vendorAccess.ownedHall(vendorId, studyHallId);
        return floorRepository.findByStudyHallIdOrderByDisplayOrderAscFloorNumberAsc(studyHallId).stream()
                .map(seatMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public FloorResponse createFloor(Long vendorId, Long studyHallId, FloorRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        if (floorRepository.existsByStudyHallIdAndFloorNumber(studyHallId, request.floorNumber())) {
            throw new DuplicateResourceException("Floor number " + request.floorNumber() + " already exists in this study hall");
        }
        int order = request.displayOrder() != null ? request.displayOrder() : floorRepository.maxDisplayOrder(studyHallId) + 1;
        Floor floor = Floor.of(hall, request.floorNumber(), request.name().trim(), order);
        floor.setDescription(trimToNull(request.description()));
        hall.getFloors().add(floor);
        return seatMapper.toResponse(floorRepository.saveAndFlush(floor));
    }

    @Override
    @Transactional
    public FloorResponse updateFloor(Long vendorId, Long studyHallId, Long floorId, FloorRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Floor floor = findFloor(studyHallId, floorId);
        if (floorRepository.existsByStudyHallIdAndFloorNumberAndIdNot(studyHallId, request.floorNumber(), floorId)) {
            throw new DuplicateResourceException("Floor number " + request.floorNumber() + " already exists in this study hall");
        }
        floor.setFloorNumber(request.floorNumber());
        floor.setName(request.name().trim());
        floor.setDescription(trimToNull(request.description()));
        if (request.displayOrder() != null) {
            floor.setDisplayOrder(request.displayOrder());
        }
        return seatMapper.toResponse(floorRepository.saveAndFlush(floor));
    }

    @Override
    @Transactional
    public void deleteFloor(Long vendorId, Long studyHallId, Long floorId) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        Floor floor = findFloor(studyHallId, floorId);
        if (blockRepository.existsByFloorId(floorId)) {
            throw new ConflictException("FLOOR_IN_USE", "This floor has blocks. Move or delete them first.");
        }
        hall.getFloors().remove(floor);
        floorRepository.delete(floor);
    }

    // ------------------------------------------------------------------ blocks

    /**
     * A grid block gets one seat per cell except the gaps, numbered {@code [prefix-]<row letter><column>}.
     * A template block gets the template's seats ({@code [prefix-]N}) and features.
     */
    @Override
    @Transactional
    public BlockResponse createBlock(Long vendorId, Long studyHallId, CreateBlockRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        Floor floor = request.floorId() != null ? findFloor(studyHallId, request.floorId()) : hall.defaultFloor();
        boolean templated = request.layoutTemplate() != null && !request.layoutTemplate().isBlank();
        String prefix = request.seatNumberPrefix() == null || request.seatNumberPrefix().isBlank()
                ? "" : request.seatNumberPrefix().trim().toUpperCase(Locale.ROOT) + "-";
        LayoutTemplateParser.Result plan = templated ? templatePlan(request, prefix) : gridPlan(request, prefix);
        if (plan.rows() * plan.columns() > properties.maxLayoutCells()) {
            throw new BusinessRuleException("A block can have at most " + properties.maxLayoutCells() + " cells");
        }
        if (plan.seatCount() == 0) {
            throw new BusinessRuleException("A block must contain at least one seat");
        }

        SeatType type = request.defaultSeatType() != null ? request.defaultSeatType() : SeatType.STANDARD;
        Set<String> takenNumbers = seatRepository.findSeatNumbersLowercase(studyHallId);
        Block block = Block.of(floor, request.name().trim(), plan.rows(), plan.columns(),
                blockRepository.maxDisplayOrder(studyHallId) + 1);
        block.setDailyPrice(request.dailyPrice());
        block.setMonthlyPrice(request.monthlyPrice());
        block.getAmenities().addAll(StudyHallManagementServiceImpl.activeAmenities(amenityRepository, request.amenityIds()));
        BlockLayout layout = BlockLayout.of(block, templated ? LayoutType.CUSTOM : LayoutType.GRID);
        for (LayoutTemplateParser.Cell cell : plan.cells()) {
            if (cell.type() != LayoutCellType.SEAT) {
                layout.addFeature(cell.row(), cell.column(), cell.type(), cell.label(), cell.direction());
                continue;
            }
            if (takenNumbers.contains(cell.seatNumber().toLowerCase(Locale.ROOT))) {
                throw new DuplicateResourceException("Seat number " + cell.seatNumber()
                        + " already exists in this study hall. Use a different seatNumberPrefix for this block.");
            }
            layout.addSeat(Seat.of(block, cell.seatNumber(), cell.row(), cell.column(), type), cell.direction());
        }
        hall.getBlocks().add(block);
        blockRepository.saveAndFlush(block);
        layoutRepository.saveAndFlush(layout);
        return seatMapper.toResponse(block);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockResponse> listBlocks(Long vendorId, Long studyHallId) {
        vendorAccess.ownedHall(vendorId, studyHallId);
        return blockRepository.findByStudyHallIdOrderByDisplayOrderAscIdAsc(studyHallId).stream()
                .map(seatMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BlockResponse updateBlock(Long vendorId, Long studyHallId, Long blockId, UpdateBlockRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Block block = findBlock(studyHallId, blockId);
        if (request.floorId() != null && !request.floorId().equals(block.getFloor().getId())) {
            block.moveTo(findFloor(studyHallId, request.floorId()));
        }
        blockLayouts.of(block).resize(request.totalRows(), request.totalColumns());
        block.setName(request.name().trim());
        block.setDailyPrice(request.dailyPrice());
        block.setMonthlyPrice(request.monthlyPrice());
        if (request.amenityIds() != null) {
            block.getAmenities().clear();
            block.getAmenities().addAll(StudyHallManagementServiceImpl.activeAmenities(amenityRepository, request.amenityIds()));
        }
        return seatMapper.toResponse(blockRepository.saveAndFlush(block));
    }

    @Override
    @Transactional
    public void deleteBlock(Long vendorId, Long studyHallId, Long blockId) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        Block block = findBlock(studyHallId, blockId);
        if (bookingRepository.existsBySeat_Block_Id(blockId)
                || block.getSeats().stream().anyMatch(s -> enrollmentRepository.existsBySeat_Id(s.getId()))) {
            throw new ConflictException("BLOCK_IN_USE", "This block has booking or student history. "
                    + "Set its seats to INACTIVE instead.");
        }
        // The layout and its cells go with the block (ON DELETE CASCADE).
        hall.getBlocks().remove(block);
        blockRepository.delete(block);
    }

    /** Replaces the walkways, entrances, tables ... of a block; its seat cells stay as they are. */
    @Override
    @Transactional
    public BlockLayoutResponse updateLayout(Long vendorId, Long studyHallId, Long blockId, UpdateLayoutRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Block block = findBlock(studyHallId, blockId);
        BlockLayout layout = blockLayouts.of(block);
        layout.clearFeatures();
        layoutRepository.flush();
        if (request.seatMoves() != null && !request.seatMoves().isEmpty()) {
            moveSeats(block, layout, request.seatMoves());
        }
        for (LayoutFeatureRequest feature : request.features()) {
            layout.addFeature(feature.row(), feature.column(), feature.type(), trimToNull(feature.label()),
                    feature.direction());
        }
        if (request.seatDirections() != null) {
            request.seatDirections().forEach(s -> layout.orientSeat(s.row(), s.column(), s.direction()));
        }
        if (request.fixtures() != null) {
            layout.replaceFixtures(request.fixtures().stream()
                    .map(f -> new LayoutFixture.Spec(f.type(), f.position(), f.offset(), f.direction(), trimToNull(f.label())))
                    .toList());
        }
        layoutRepository.saveAndFlush(layout);
        return seatMapper.toLayoutResponse(block, layout, null);
    }

    /**
     * Moves seats of this block to other cells in one step (ids, numbers, bookings and facing kept), e.g.
     * after a row/column was inserted or deleted. Targets must be inside the grid, distinct and not taken
     * by a seat that stays. Positions are unique immediately in the database, so the moving seats' cells
     * are dropped, the seats parked outside the grid, then placed and given their cells back.
     */
    private void moveSeats(Block block, BlockLayout layout, List<SeatMoveRequest> moves) {
        Map<Long, Seat> seats = new HashMap<>();
        block.getSeats().forEach(seat -> seats.put(seat.getId(), seat));
        Set<Long> moving = new HashSet<>();
        Set<Long> targets = new HashSet<>();
        for (SeatMoveRequest move : moves) {
            if (!seats.containsKey(move.seatId())) {
                throw new BusinessRuleException("INVALID_SEAT_MOVE", "Seat " + move.seatId() + " is not in this block");
            }
            if (!moving.add(move.seatId())) {
                throw new BusinessRuleException("INVALID_SEAT_MOVE", "Seat " + move.seatId() + " is moved twice");
            }
            if (!layout.contains(move.row(), move.column())) {
                throw new BusinessRuleException("INVALID_SEAT_MOVE", "Position (" + move.row() + "," + move.column()
                        + ") is outside the " + layout.getRows() + "x" + layout.getColumns() + " grid");
            }
            if (!targets.add((long) move.row() << 32 | move.column())) {
                throw new BusinessRuleException("INVALID_SEAT_MOVE", "Two seats moved to (" + move.row() + "," + move.column() + ")");
            }
        }
        for (Seat seat : seats.values()) {
            if (!moving.contains(seat.getId()) && targets.contains((long) seat.getRowIndex() << 32 | seat.getColumnIndex())) {
                throw new DuplicateResourceException("There is already a seat at (" + seat.getRowIndex() + ","
                        + seat.getColumnIndex() + ")");
            }
        }
        Map<Long, Direction> facing = new HashMap<>();
        layout.getCells().stream().filter(c -> c.isSeat() && moving.contains(c.getSeat().getId()))
                .forEach(c -> facing.put(c.getSeat().getId(), c.getDirection()));
        moves.forEach(m -> layout.removeSeatCell(seats.get(m.seatId())));
        layoutRepository.flush();
        moves.forEach(m -> {
            Seat seat = seats.get(m.seatId());
            seat.moveTo(block, seat.getRowIndex() + PARK_OFFSET, seat.getColumnIndex());
        });
        seatRepository.flush();
        moves.forEach(m -> seats.get(m.seatId()).moveTo(block, m.row(), m.column()));
        seatRepository.flush();
        moves.forEach(m -> layout.addSeat(seats.get(m.seatId()), facing.get(m.seatId())));
        layoutRepository.flush();
    }

    /** Rows beyond any grid (max 60) where moving seats wait so positions never collide mid-move. */
    private static final int PARK_OFFSET = 1000;

    // ------------------------------------------------------------------- seats

    @Override
    @Transactional
    public SeatResponse createSeat(Long vendorId, Long studyHallId, CreateSeatRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Block block = findBlock(studyHallId, request.blockId());
        BlockLayout layout = blockLayouts.of(block);
        layout.requireFree(request.row(), request.column());
        String seatNumber = request.seatNumber().trim().toUpperCase(Locale.ROOT);
        if (seatRepository.existsByStudyHallIdAndSeatNumberIgnoreCase(studyHallId, seatNumber)) {
            throw new DuplicateResourceException("Seat number " + seatNumber + " already exists in this study hall");
        }
        Seat seat = Seat.of(block, seatNumber, request.row(), request.column(), request.seatType());
        seat.setPricePerDay(request.pricePerDay());
        seat.setPricePerMonth(request.pricePerMonth());
        layout.addSeat(seat, request.direction());
        return seatMapper.toResponse(seatRepository.saveAndFlush(seat));
    }

    @Override
    @Transactional
    public SeatResponse updateSeat(Long vendorId, Long studyHallId, Long seatId, UpdateSeatRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Seat seat = seatRepository.findByIdAndStudyHallId(seatId, studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));
        String seatNumber = request.seatNumber().trim().toUpperCase(Locale.ROOT);
        if (seatRepository.existsByStudyHallIdAndSeatNumberIgnoreCaseAndIdNot(studyHallId, seatNumber, seatId)) {
            throw new DuplicateResourceException("Seat number " + seatNumber + " already exists in this study hall");
        }
        if (seat.getStatus() == SeatStatus.RESERVED && request.status() != SeatStatus.RESERVED
                && enrollmentRepository.existsBySeat_IdAndStatus(seatId, EnrollmentStatus.ACTIVE)) {
            throw new ConflictException("SEAT_HELD", "This seat is held by an active student enrollment; "
                    + "end or move the enrollment first");
        }
        seat.setSeatNumber(seatNumber);
        seat.setSeatType(request.seatType());
        seat.setStatus(request.status());
        seat.setPricePerDay(request.pricePerDay());
        seat.setPricePerMonth(request.pricePerMonth());
        return seatMapper.toResponse(seatRepository.saveAndFlush(seat));
    }

    @Override
    @Transactional
    public void deleteSeat(Long vendorId, Long studyHallId, Long seatId) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Seat seat = seatRepository.findByIdAndStudyHallId(seatId, studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));
        if (bookingRepository.existsBySeat_Id(seatId) || enrollmentRepository.existsBySeat_Id(seatId)) {
            throw new ConflictException("SEAT_IN_USE", "This seat has booking or student history. Set it INACTIVE instead.");
        }
        // Its layout cell goes with it (ON DELETE CASCADE); the position becomes EMPTY.
        seat.getBlock().getSeats().remove(seat);
        seatRepository.delete(seat);
    }

    // ----------------------------------------------------------------- helpers

    private LayoutTemplateParser.Result gridPlan(CreateBlockRequest request, String prefix) {
        if (request.totalRows() == null || request.totalColumns() == null) {
            throw new BusinessRuleException("totalRows and totalColumns are required without a layoutTemplate");
        }
        int rows = request.totalRows();
        int columns = request.totalColumns();
        Set<GridPosition> gaps = new HashSet<>(request.gaps() == null ? List.of() : request.gaps());
        for (GridPosition gap : gaps) {
            if (gap.row() > rows || gap.column() > columns) {
                throw new BusinessRuleException("Gap (" + gap.row() + "," + gap.column() + ") is outside the "
                        + rows + "x" + columns + " grid");
            }
        }
        List<LayoutTemplateParser.Cell> cells = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int column = 1; column <= columns; column++) {
                if (!gaps.contains(new GridPosition(row, column))) {
                    cells.add(new LayoutTemplateParser.Cell(row, column, LayoutCellType.SEAT, null,
                            prefix + SeatNumbering.rowLabel(row) + column));
                }
            }
        }
        return new LayoutTemplateParser.Result(rows, columns, cells);
    }

    private static LayoutTemplateParser.Result templatePlan(CreateBlockRequest request, String prefix) {
        if (request.gaps() != null && !request.gaps().isEmpty()) {
            throw new BusinessRuleException("Use '.' in the layoutTemplate instead of gaps");
        }
        LayoutTemplateParser.Result parsed = LayoutTemplateParser.parse(request.layoutTemplate());
        List<LayoutTemplateParser.Cell> cells = parsed.cells().stream()
                .map(c -> c.type() == LayoutCellType.SEAT
                        ? new LayoutTemplateParser.Cell(c.row(), c.column(), c.type(), null, prefix + c.seatNumber(),
                                c.direction())
                        : c)
                .toList();
        cells.stream().filter(c -> c.seatNumber() != null && c.seatNumber().length() > 20).findFirst().ifPresent(c -> {
            throw new BusinessRuleException("INVALID_LAYOUT_TEMPLATE", "Seat number " + c.seatNumber()
                    + " is longer than 20 characters");
        });
        return new LayoutTemplateParser.Result(parsed.rows(), parsed.columns(), cells);
    }

    private Floor findFloor(Long studyHallId, Long floorId) {
        return floorRepository.findByIdAndStudyHallId(floorId, studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", floorId));
    }

    private Block findBlock(Long studyHallId, Long blockId) {
        return blockRepository.findByIdAndStudyHallId(blockId, studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Block", blockId));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
