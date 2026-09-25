package com.spacz.admin.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Study hall as returned by studyhall-service. Nested collections are passed through unchanged.
 */
public record StudyHallDetailDto(Long id, Long vendorId, String vendorBusinessName, String name, String description,
                                 String addressLine, String city, String state, String pincode, Double latitude,
                                 Double longitude, String contactPhone, String contactEmail, BigDecimal pricePerDay,
                                 BigDecimal pricePerMonth, String rules, String status, String statusReason,
                                 JsonNode images, JsonNode amenities, JsonNode programs, JsonNode operatingHours,
                                 JsonNode blocks, long totalSeats, Instant submittedAt, Instant approvedAt,
                                 Instant createdAt, Instant updatedAt) {
}
