package com.spacz.admin.client.dto;

import java.math.BigDecimal;

public record StudyHallSummaryDto(Long id, Long vendorId, String vendorBusinessName, String name, String addressLine,
                                  String city, String state, BigDecimal pricePerDay, BigDecimal pricePerMonth,
                                  String status, String coverImageUrl, long seatCount) {
}
