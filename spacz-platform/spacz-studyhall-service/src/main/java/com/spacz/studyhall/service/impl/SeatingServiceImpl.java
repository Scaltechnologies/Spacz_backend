package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.config.StudyHallProperties;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.CreateSeatRequest;
import com.spacz.studyhall.dto.seat.GridPosition;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.dto.seat.UpdateBlockRequest;
import com.spacz.studyhall.dto.seat.UpdateSeatRequest;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.EnrollmentStatus;
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
import com.spacz.studyhall.repository.BlockRepository;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.EnrollmentRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.service.SeatingService;
import com.spacz.studyhall.service.support.SeatNumbering;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SeatingServiceImpl implements SeatingService {

    private final VendorAccess vendorAccess;
    private final BlockRepository blockRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AmenityRepository amenityRepository;
    private final SeatMapper seatMapper;
    private final StudyHallProperties properties;

    /**
     * Generates one seat per grid cell except the gaps, numbered {@code [prefix-]<row letter><column>}.
     */
    @Override
    @Transactional
    public BlockResponse createBlock(Long vendorId, Long studyHallId, CreateBlockRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        int rows = request.totalRows();
        int columns = request.totalColumns();
        if (rows * columns > properties.maxLayoutCells()) {
            throw new BusinessRuleException("A block can have at most " + properties.maxLayoutCells() + " cells");
        }
        Set<GridPosition> gaps = new HashSet<>(request.gaps() == null ? List.of() : request.gaps());
        for (GridPosition gap : gaps) {
            if (gap.row() > rows || gap.column() > columns) {
                throw new BusinessRuleException("Gap (" + gap.row() + "," + gap.column() + ") is outside the "
                        + rows + "x" + columns + " grid");
            }
        }
        if (gaps.size() >= rows * columns) {
            throw new BusinessRuleException("A block must contain at least one seat");
        }

        String prefix = request.seatNumberPrefix() == null || request.seatNumberPrefix().isBlank()
                ? "" : request.seatNumberPrefix().trim().toUpperCase(Locale.ROOT) + "-";
        SeatType type = request.defaultSeatType() != null ? request.defaultSeatType() : SeatType.STANDARD;
        Set<String> takenNumbers = seatRepository.findSeatNumbersLowercase(studyHallId);

        Block block = Block.of(hall, request.name().trim(), rows, columns, blockRepository.maxDisplayOrder(studyHallId) + 1);
        block.setDailyPrice(request.dailyPrice());
        block.setMonthlyPrice(request.monthlyPrice());
        block.getAmenities().addAll(StudyHallManagementServiceImpl.activeAmenities(amenityRepository, request.amenityIds()));
        for (int row = 1; row <= rows; row++) {
            for (int column = 1; column <= columns; column++) {
                if (gaps.contains(new GridPosition(row, column))) {
                    continue;
                }
                String seatNumber = prefix + SeatNumbering.rowLabel(row) + column;
                if (takenNumbers.contains(seatNumber.toLowerCase(Locale.ROOT))) {
                    throw new DuplicateResourceException("Seat number " + seatNumber
                            + " already exists in this study hall. Use a different seatNumberPrefix for this block.");
                }
                block.getSeats().add(Seat.of(block, seatNumber, row, column, type));
            }
        }
        hall.getBlocks().add(block);
        return seatMapper.toResponse(blockRepository.saveAndFlush(block));
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
        boolean seatOutside = block.getSeats().stream()
                .anyMatch(s -> s.getRowIndex() > request.totalRows() || s.getColumnIndex() > request.totalColumns());
        if (seatOutside) {
            throw new BusinessRuleException("Seats exist outside a " + request.totalRows() + "x" + request.totalColumns()
                    + " grid; move or remove them first");
        }
        block.setName(request.name().trim());
        block.setTotalRows(request.totalRows());
        block.setTotalColumns(request.totalColumns());
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
        hall.getBlocks().remove(block);
        blockRepository.delete(block);
    }

    @Override
    @Transactional
    public SeatResponse createSeat(Long vendorId, Long studyHallId, CreateSeatRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Block block = findBlock(studyHallId, request.blockId());
        if (!block.contains(request.row(), request.column())) {
            throw new BusinessRuleException("Position (" + request.row() + "," + request.column() + ") is outside the "
                    + block.getTotalRows() + "x" + block.getTotalColumns() + " grid");
        }
        if (block.isOccupied(request.row(), request.column())) {
            throw new DuplicateResourceException("There is already a seat at (" + request.row() + "," + request.column() + ")");
        }
        String seatNumber = request.seatNumber().trim().toUpperCase(Locale.ROOT);
        if (seatRepository.existsByStudyHallIdAndSeatNumberIgnoreCase(studyHallId, seatNumber)) {
            throw new DuplicateResourceException("Seat number " + seatNumber + " already exists in this study hall");
        }
        Seat seat = Seat.of(block, seatNumber, request.row(), request.column(), request.seatType());
        seat.setPricePerDay(request.pricePerDay());
        seat.setPricePerMonth(request.pricePerMonth());
        block.getSeats().add(seat);
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
        seat.getBlock().getSeats().remove(seat);
        seatRepository.delete(seat);
    }

    private Block findBlock(Long studyHallId, Long blockId) {
        return blockRepository.findByIdAndStudyHallId(blockId, studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Block", blockId));
    }
}
