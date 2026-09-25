package com.spacz.studyhall.dto.hall;

import io.swagger.v3.oas.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Query parameters of GET /api/studyhalls (bound from the query string).
 */
public record StudyHallSearchCriteria(
        @Parameter(description = "Free text over name, description, address and city") String search,
        @Parameter(description = "Exact city (case-insensitive)", example = "Hyderabad") String city,
        @Parameter(description = "Exam/course the hall supports (ID from /api/programs)") Long programId,
        @Parameter(description = "Hall must offer ALL of these amenity IDs (hall or block level)") Set<Long> amenityIds,
        @Parameter(description = "Minimum default price per day") BigDecimal minPrice,
        @Parameter(description = "Maximum default price per day") BigDecimal maxPrice,
        @Parameter(description = "Search centre latitude (with longitude and radiusKm)") Double latitude,
        @Parameter(description = "Search centre longitude") Double longitude,
        @Parameter(description = "Radius in km (default 10 when latitude/longitude are given)") Double radiusKm,
        @Parameter(description = "Only halls with a free seat from this date (needs availableTo)") LocalDate availableFrom,
        @Parameter(description = "... to this date, inclusive") LocalDate availableTo) {
}
