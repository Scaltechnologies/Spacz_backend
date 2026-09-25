package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.CreateSeatRequest;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.dto.seat.UpdateBlockRequest;
import com.spacz.studyhall.dto.seat.UpdateSeatRequest;

import java.util.List;

/**
 * Blocks (sections laid out as grids) and seats of a vendor's study hall.
 */
public interface SeatingService {

    BlockResponse createBlock(Long vendorId, Long studyHallId, CreateBlockRequest request);

    List<BlockResponse> listBlocks(Long vendorId, Long studyHallId);

    BlockResponse updateBlock(Long vendorId, Long studyHallId, Long blockId, UpdateBlockRequest request);

    void deleteBlock(Long vendorId, Long studyHallId, Long blockId);

    SeatResponse createSeat(Long vendorId, Long studyHallId, CreateSeatRequest request);

    SeatResponse updateSeat(Long vendorId, Long studyHallId, Long seatId, UpdateSeatRequest request);

    void deleteSeat(Long vendorId, Long studyHallId, Long seatId);
}
