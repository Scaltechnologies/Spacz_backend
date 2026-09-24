package com.studyhouse.spacz.partner.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhouse.spacz.partner.dto.request.SeatRequest;
import com.studyhouse.spacz.partner.dto.response.SeatResponse;
import com.studyhouse.spacz.partner.entity.Block;
import com.studyhouse.spacz.partner.entity.Seat;
import com.studyhouse.spacz.partner.exception.BadRequestException;
import com.studyhouse.spacz.partner.exception.ResourceNotFoundException;
import com.studyhouse.spacz.partner.repository.BlockRepository;
import com.studyhouse.spacz.partner.repository.SeatRepository;

@Service
@Transactional
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final BlockRepository blockRepository;

    public SeatServiceImpl(SeatRepository seatRepository, BlockRepository blockRepository) {
        this.seatRepository = seatRepository;
        this.blockRepository = blockRepository;
    }

    @Override
    public SeatResponse createSeat(SeatRequest request) {
        if (request.blockId() == null) {
            throw new BadRequestException("block.blockId is required");
        }
        Seat seat = new Seat();
        seat.setBlock(findBlock(request.blockId()));
        seat.setSeatNumber(request.seatNumber());
        seat.setReserved(Boolean.TRUE.equals(request.reserved()));
        seat.setSeatPrice(request.seatPrice() == null ? 0 : request.seatPrice());
        return SeatResponse.from(seatRepository.saveAndFlush(seat));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getAllSeats() {
        return seatRepository.findAll(Sort.by("seatId")).stream().map(SeatResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SeatResponse getSeatById(Long id) {
        return SeatResponse.from(findSeat(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsByBlockId(Long blockId) {
        findBlock(blockId);
        return seatRepository.findByBlockBlockIdOrderBySeatIdAsc(blockId).stream().map(SeatResponse::from).toList();
    }

    // As in the legacy service, seat number and reserved flag are replaced with the
    // values sent (reserved not sent = false) and the `block` sent is applied (it must
    // exist). The legacy service cleared the block when none was sent and ignored the
    // price; here an omitted block/price is left unchanged and a sent price is saved.
    @Override
    public SeatResponse updateSeat(Long id, SeatRequest request) {
        Seat seat = findSeat(id);
        if (request.blockId() != null) {
            seat.setBlock(findBlock(request.blockId()));
        }
        seat.setSeatNumber(request.seatNumber());
        seat.setReserved(Boolean.TRUE.equals(request.reserved()));
        if (request.seatPrice() != null) {
            seat.setSeatPrice(request.seatPrice());
        }
        return SeatResponse.from(seatRepository.saveAndFlush(seat));
    }

    // Unknown IDs are ignored (as before). A seat that still has a booking
    // (booking.seat_id) is rejected by the database and answered with 409 Conflict.
    @Override
    public void deleteSeat(Long id) {
        seatRepository.findById(id).ifPresent(seat -> {
            seatRepository.delete(seat);
            seatRepository.flush();
        });
    }

    private Seat findSeat(Long id) {
        return seatRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Seat", id));
    }

    private Block findBlock(Long id) {
        return blockRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Block", id));
    }
}
