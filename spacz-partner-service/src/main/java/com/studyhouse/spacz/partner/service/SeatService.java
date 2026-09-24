package com.studyhouse.spacz.partner.service;

import java.util.List;

import com.studyhouse.spacz.partner.dto.request.SeatRequest;
import com.studyhouse.spacz.partner.dto.response.SeatResponse;

public interface SeatService {

    SeatResponse createSeat(SeatRequest request);

    List<SeatResponse> getAllSeats();

    SeatResponse getSeatById(Long id);

    List<SeatResponse> getSeatsByBlockId(Long blockId);

    SeatResponse updateSeat(Long id, SeatRequest request);

    void deleteSeat(Long id);
}
