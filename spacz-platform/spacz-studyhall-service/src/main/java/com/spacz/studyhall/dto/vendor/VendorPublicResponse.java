package com.spacz.studyhall.dto.vendor;

/**
 * What students see about an approved vendor/institute.
 */
public record VendorPublicResponse(Long vendorId, String businessName, String city, String state, String description,
                                   String logoUrl, long liveStudyHalls) {
}
