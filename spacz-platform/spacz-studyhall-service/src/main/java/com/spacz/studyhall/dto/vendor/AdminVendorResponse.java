package com.spacz.studyhall.dto.vendor;

/**
 * Vendor profile with study-hall counts, for admin screens.
 */
public record AdminVendorResponse(VendorProfileResponse profile, long studyHallCount, long activeStudyHallCount) {
}
