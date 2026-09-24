package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSearchCriteria;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.seat.AvailabilityResponse;
import com.spacz.studyhall.dto.seat.SeatMapResponse;
import com.spacz.studyhall.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Read views. Everyone sees live halls; the owning vendor and admins see any status.
 */
public interface StudyHallQueryService {

    PageResponse<StudyHallSummaryResponse> search(StudyHallSearchCriteria criteria, Pageable pageable);

    StudyHallDetailResponse get(Long studyHallId, AuthenticatedUser caller);

    SeatMapResponse seatMap(Long studyHallId, AuthenticatedUser caller, LocalDate startDate, LocalDate endDate);

    AvailabilityResponse availability(Long studyHallId, AuthenticatedUser caller, LocalDate startDate, LocalDate endDate);
}
