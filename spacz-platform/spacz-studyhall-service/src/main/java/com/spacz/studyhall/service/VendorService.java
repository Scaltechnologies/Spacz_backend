package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.vendor.AdminVendorResponse;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileResponse;
import com.spacz.studyhall.dto.vendor.VendorPublicResponse;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.VendorStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

public interface VendorService {

    /** POST /api/vendors: creates the caller's profile (DRAFT) if the account has none yet. */
    VendorProfileResponse create(AuthenticatedUser vendor, VendorProfileRequest request);

    VendorProfileResponse getMine(Long vendorId);

    VendorProfileResponse updateMine(AuthenticatedUser vendor, VendorProfileRequest request);

    /** DRAFT/REJECTED → PENDING once the profile is complete. */
    VendorProfileResponse submit(AuthenticatedUser vendor);

    /** Public card of an operational vendor. */
    VendorPublicResponse getPublic(Long vendorId);

    /** Idempotent on vendorId (called by auth-service at registration). */
    CreationResult createFromRegistration(CreateVendorProfileRequest request);

    PageResponse<AdminVendorResponse> search(String search, VendorStatus status, String city, Pageable pageable);

    AdminVendorResponse getForAdmin(Long vendorId);

    /** Admin action; approving/activating a vendor also takes its approved halls live. */
    AdminVendorResponse applyAction(Long vendorId, StatusAction action, String reason);

    record CreationResult(VendorProfileResponse profile, boolean created) {
    }
}
