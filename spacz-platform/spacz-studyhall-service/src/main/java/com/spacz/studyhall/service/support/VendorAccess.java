package com.spacz.studyhall.service.support;

import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.entity.VendorStatus;
import com.spacz.studyhall.exception.ForbiddenException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ownership checks. The vendorId always comes from the verified JWT, never from the request.
 * Another vendor's hall is reported as "not found" so its existence is not revealed.
 */
@Component
@RequiredArgsConstructor
public class VendorAccess {

    private final VendorProfileRepository vendorRepository;
    private final StudyHallRepository studyHallRepository;

    public VendorProfile vendor(Long vendorId) {
        return vendorRepository.findByVendorId(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vendor profile not found for the current account. Create it with POST /api/vendors"));
    }

    /** The vendor, if allowed to make changes (suspended vendors are read-only). */
    public VendorProfile writableVendor(Long vendorId) {
        VendorProfile vendor = vendor(vendorId);
        ensureWritable(vendor);
        return vendor;
    }

    public StudyHall ownedHall(Long vendorId, Long studyHallId) {
        return studyHallRepository.findOwned(studyHallId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Study hall", studyHallId));
    }

    public StudyHall writableHall(Long vendorId, Long studyHallId) {
        StudyHall hall = ownedHall(vendorId, studyHallId);
        ensureWritable(hall.getVendor());
        return hall;
    }

    /**
     * A hall as seen by the caller: live halls for everyone; any status for the owning vendor and admins.
     */
    public StudyHall viewableHall(Long studyHallId, AuthenticatedUser caller) {
        StudyHall hall = studyHallRepository.findWithVendor(studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Study hall", studyHallId));
        boolean privileged = caller != null && (caller.isAdmin() || (caller.isVendor() && hall.isOwnedBy(caller.userId())));
        if (!privileged && !hall.isBookable()) {
            throw new ResourceNotFoundException("Study hall", studyHallId);
        }
        return hall;
    }

    public static void ensureWritable(VendorProfile vendor) {
        if (vendor.getStatus() == VendorStatus.SUSPENDED) {
            throw new ForbiddenException("VENDOR_SUSPENDED",
                    "Your vendor account is suspended" + (vendor.getStatusReason() != null
                            ? ": " + vendor.getStatusReason() : "") + ". Contact SPACZ support.");
        }
    }
}
