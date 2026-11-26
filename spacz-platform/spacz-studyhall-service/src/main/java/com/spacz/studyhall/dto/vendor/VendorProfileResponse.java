package com.spacz.studyhall.dto.vendor;

import com.spacz.studyhall.entity.VendorStatus;

import java.time.Instant;
import java.util.List;

/**
 * @param missingForSubmission fields still needed before {@code POST /api/vendors/me/submit}
 * @param payAtHallEnabled     whether students may choose Pay at Hall at this vendor's halls (admin switch)
 */
public record VendorProfileResponse(Long id, Long vendorId, String businessName, String contactName, String email,
                                    String phone, String addressLine, String city, String state, String pincode,
                                    String gstNumber, String description, String logoUrl, VendorStatus status,
                                    String statusReason, List<String> missingForSubmission, Instant submittedAt,
                                    Instant approvedAt, Instant createdAt, Instant updatedAt,
                                    boolean payAtHallEnabled) {
}
