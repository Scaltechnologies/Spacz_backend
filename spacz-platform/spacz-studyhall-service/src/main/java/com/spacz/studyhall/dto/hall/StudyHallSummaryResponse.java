package com.spacz.studyhall.dto.hall;

import com.spacz.studyhall.entity.StudyHallStatus;

import java.math.BigDecimal;
import java.util.List;

public record StudyHallSummaryResponse(Long id, Long vendorId, String vendorBusinessName, String name, String addressLine,
                                       String city, String state, Double latitude, Double longitude,
                                       BigDecimal pricePerDay, BigDecimal pricePerMonth, StudyHallStatus status,
                                       String coverImageUrl, List<String> amenities, List<ProgramRef> programs,
                                       long seatCount, Double distanceKm) {
}
