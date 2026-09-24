package com.spacz.admin.client.dto;

import java.time.Instant;
import java.util.List;

public record VendorProfileDto(Long id, Long vendorId, String businessName, String contactName, String email,
                               String phone, String addressLine, String city, String state, String pincode,
                               String gstNumber, String description, String logoUrl, String status,
                               String statusReason, List<String> missingForSubmission, Instant submittedAt,
                               Instant approvedAt, Instant createdAt, Instant updatedAt) {
}
