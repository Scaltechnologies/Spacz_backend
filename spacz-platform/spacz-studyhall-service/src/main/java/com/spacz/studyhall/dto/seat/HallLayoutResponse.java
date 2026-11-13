package com.spacz.studyhall.dto.seat;

import java.time.LocalDate;
import java.util.List;

/** Floors → blocks → layout cells, with seat availability for [startDate, endDate]. */
public record HallLayoutResponse(Long studyHallId, LocalDate startDate, LocalDate endDate,
                                 List<FloorLayoutResponse> floors) {
}
