package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.studyhouse.spacz.entity.Seat;
import com.studyhouse.spacz.repository.SeatRepository;

@Service
public class SeatServiceImpl implements SeatService {
    @Autowired
    private SeatRepository seatRepository;

    @Override
    public Seat saveSeat(Seat seat) {
        return seatRepository.save(seat);
    }

    @Override
    public List<Seat> getAllSeats() {
        return seatRepository.findAll();
    }

    @Override
    public Optional<Seat> getSeatById(Long id) {
        return seatRepository.findById(id);
    }

    @Override
    public Seat updateSeat(Long id, Seat seat) {
        Seat existingSeat = seatRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Seat not found"));
        existingSeat.setBlock(seat.getBlock());
        existingSeat.setSeatNumber(seat.getSeatNumber());
        existingSeat.setReserved(seat.isReserved());
        return seatRepository.save(existingSeat);
    }

    @Override
    public void deleteSeat(Long id) {
        seatRepository.deleteById(id);
    }
}
