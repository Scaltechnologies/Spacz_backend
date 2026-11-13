package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.seat.BlockLayoutResponse;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.CreateBlockRequest;
import com.spacz.studyhall.dto.seat.CreateSeatRequest;
import com.spacz.studyhall.dto.seat.FloorRequest;
import com.spacz.studyhall.dto.seat.FloorResponse;
import com.spacz.studyhall.dto.seat.SeatResponse;
import com.spacz.studyhall.dto.seat.UpdateBlockRequest;
import com.spacz.studyhall.dto.seat.UpdateLayoutRequest;
import com.spacz.studyhall.dto.seat.UpdateSeatRequest;

import java.util.List;

/**
 * Floors, blocks (with their physical layouts) and seats of a vendor's study hall.
 */
public interface SeatingService {

    List<FloorResponse> listFloors(Long vendorId, Long studyHallId);

    FloorResponse createFloor(Long vendorId, Long studyHallId, FloorRequest request);

    FloorResponse updateFloor(Long vendorId, Long studyHallId, Long floorId, FloorRequest request);

    void deleteFloor(Long vendorId, Long studyHallId, Long floorId);

    BlockResponse createBlock(Long vendorId, Long studyHallId, CreateBlockRequest request);

    List<BlockResponse> listBlocks(Long vendorId, Long studyHallId);

    BlockResponse updateBlock(Long vendorId, Long studyHallId, Long blockId, UpdateBlockRequest request);

    void deleteBlock(Long vendorId, Long studyHallId, Long blockId);

    BlockLayoutResponse updateLayout(Long vendorId, Long studyHallId, Long blockId, UpdateLayoutRequest request);

    SeatResponse createSeat(Long vendorId, Long studyHallId, CreateSeatRequest request);

    SeatResponse updateSeat(Long vendorId, Long studyHallId, Long seatId, UpdateSeatRequest request);

    void deleteSeat(Long vendorId, Long studyHallId, Long seatId);
}
