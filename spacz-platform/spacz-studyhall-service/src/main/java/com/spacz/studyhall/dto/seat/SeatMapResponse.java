package com.spacz.studyhall.dto.seat;

import java.time.LocalDate;
import java.util.List;

public record SeatMapResponse(Long studyHallId, LocalDate startDate, LocalDate endDate, List<BlockResponse> blocks) {
}
