package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.mapper.StudyHallMapper;
import com.spacz.studyhall.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds hall responses, loading seat counts for a whole page in one query.
 */
@Component
@RequiredArgsConstructor
class HallViewAssembler {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final SeatRepository seatRepository;
    private final StudyHallMapper mapper;

    PageResponse<StudyHallSummaryResponse> page(Page<StudyHall> page, Double fromLatitude, Double fromLongitude) {
        return new PageResponse<>(summaries(page.getContent(), fromLatitude, fromLongitude), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    List<StudyHallSummaryResponse> summaries(List<StudyHall> halls, Double fromLatitude, Double fromLongitude) {
        Map<Long, Long> seatCounts = new HashMap<>();
        if (!halls.isEmpty()) {
            seatRepository.countByHallIdsAndStatus(halls.stream().map(StudyHall::getId).toList(), SeatStatus.AVAILABLE)
                    .forEach(row -> seatCounts.put((Long) row[0], (Long) row[1]));
        }
        return halls.stream()
                .map(hall -> mapper.toSummary(hall, seatCounts.getOrDefault(hall.getId(), 0L),
                        distanceKm(fromLatitude, fromLongitude, hall.getLatitude(), hall.getLongitude())))
                .toList();
    }

    StudyHallDetailResponse detail(StudyHall hall) {
        return mapper.toDetail(hall, seatRepository.countByStudyHallId(hall.getId()));
    }

    /** Haversine distance, rounded to 0.1 km; null when either point is unknown. */
    static Double distanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(km * 10.0) / 10.0;
    }
}
