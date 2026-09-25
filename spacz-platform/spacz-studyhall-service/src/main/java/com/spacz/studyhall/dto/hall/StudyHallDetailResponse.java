package com.spacz.studyhall.dto.hall;

import com.spacz.studyhall.entity.StudyHallStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StudyHallDetailResponse(Long id, Long vendorId, String vendorBusinessName, String name,
                                      String description, String addressLine, String city, String state,
                                      String pincode, Double latitude, Double longitude, String contactPhone,
                                      String contactEmail, BigDecimal pricePerDay, BigDecimal pricePerMonth,
                                      String rules, StudyHallStatus status, String statusReason,
                                      List<ImageResponse> images, List<AmenityResponse> amenities,
                                      List<ProgramRef> programs, List<OperatingHoursResponse> operatingHours,
                                      List<BlockSummary> blocks, long totalSeats, Instant submittedAt,
                                      Instant approvedAt, Instant createdAt, Instant updatedAt) {
}
