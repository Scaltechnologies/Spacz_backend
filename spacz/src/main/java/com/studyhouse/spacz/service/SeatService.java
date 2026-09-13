package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import com.studyhouse.spacz.entity.Seat;

public interface SeatService {
    Seat saveSeat(Seat seat);
    List<Seat> getAllSeats();
    Optional<Seat> getSeatById(Long id);
    Seat updateSeat(Long id, Seat seat);
    void deleteSeat(Long id);
}
