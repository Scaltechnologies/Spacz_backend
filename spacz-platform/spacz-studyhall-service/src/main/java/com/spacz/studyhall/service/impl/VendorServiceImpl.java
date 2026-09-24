package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.audit.AuditEvents;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.vendor.AdminVendorResponse;
import com.spacz.studyhall.dto.vendor.CreateVendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileRequest;
import com.spacz.studyhall.dto.vendor.VendorProfileResponse;
import com.spacz.studyhall.dto.vendor.VendorPublicResponse;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.entity.VendorStatus;
import com.spacz.studyhall.exception.DuplicateResourceException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.mapper.StudyHallMapper;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.repository.VendorSpecifications;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.VendorService;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorProfileRepository vendorRepository;
    private final StudyHallRepository studyHallRepository;
    private final VendorAccess vendorAccess;
    private final StudyHallMapper mapper;
    private final AuditEvents auditEvents;
    private final Clock clock;

    @Override
    @Transactional
    public VendorProfileResponse create(AuthenticatedUser vendor, VendorProfileRequest request) {
        if (vendorRepository.existsByVendorId(vendor.userId())) {
            throw new DuplicateResourceException("A vendor profile already exists for this account; use PUT /api/vendors/me");
        }
        VendorProfile profile = VendorProfile.draft(vendor.userId());
        apply(profile, request);
        if (profile.getEmail() == null) {
            profile.setEmail(vendor.email());
        }
        return mapper.toResponse(vendorRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorProfileResponse getMine(Long vendorId) {
        return mapper.toResponse(vendorAccess.vendor(vendorId));
    }

    @Override
    @Transactional
    public VendorProfileResponse updateMine(AuthenticatedUser vendor, VendorProfileRequest request) {
        VendorProfile profile = vendorAccess.writableVendor(vendor.userId());
        apply(profile, request);
        return mapper.toResponse(vendorRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional
    public VendorProfileResponse submit(AuthenticatedUser vendor) {
        VendorProfile profile = vendorAccess.writableVendor(vendor.userId());
        profile.submit(clock.instant());
        auditEvents.record(vendor, "VENDOR_SUBMITTED", "VENDOR", profile.getVendorId(),
                "Vendor " + profile.displayName() + " submitted for approval");
        return mapper.toResponse(vendorRepository.saveAndFlush(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorPublicResponse getPublic(Long vendorId) {
        VendorProfile profile = vendorRepository.findByVendorId(vendorId)
                .filter(VendorProfile::isOperational)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));
        return mapper.toPublic(profile, studyHallRepository.countByVendor_IdAndStatus(profile.getId(), StudyHallStatus.ACTIVE));
    }

    @Override
    @Transactional
    public CreationResult createFromRegistration(CreateVendorProfileRequest request) {
        Optional<VendorProfile> existing = vendorRepository.findByVendorId(request.vendorId());
        if (existing.isPresent()) {
            return new CreationResult(mapper.toResponse(existing.get()), false);
        }
        VendorProfile profile = VendorProfile.draft(request.vendorId());
        profile.setEmail(trimToNull(request.email()));
        profile.setBusinessName(trimToNull(request.businessName()));
        profile.setContactName(trimToNull(request.contactName()));
        profile.setPhone(trimToNull(request.phone()));
        profile.setCity(trimToNull(request.city()));
        VendorProfile saved = vendorRepository.saveAndFlush(profile);
        log.info("Vendor profile created for vendor {} (DRAFT)", request.vendorId());
        return new CreationResult(mapper.toResponse(saved), true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminVendorResponse> search(String search, VendorStatus status, String city, Pageable pageable) {
        Page<VendorProfile> page = vendorRepository.findAll(VendorSpecifications.matching(search, status, city), pageable);
        Map<Long, Long> hallCounts = new HashMap<>();
        Map<Long, Long> activeCounts = new HashMap<>();
        if (page.hasContent()) {
            var profileIds = page.getContent().stream().map(VendorProfile::getId).toList();
            studyHallRepository.countByVendorIds(profileIds).forEach(row -> hallCounts.put((Long) row[0], (Long) row[1]));
            studyHallRepository.countByVendorIdsAndStatus(profileIds, StudyHallStatus.ACTIVE)
                    .forEach(row -> activeCounts.put((Long) row[0], (Long) row[1]));
        }
        return PageResponse.from(page, vendor -> new AdminVendorResponse(mapper.toResponse(vendor),
                hallCounts.getOrDefault(vendor.getId(), 0L), activeCounts.getOrDefault(vendor.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminVendorResponse getForAdmin(Long vendorId) {
        return toAdminResponse(find(vendorId));
    }

    @Override
    @Transactional
    public AdminVendorResponse applyAction(Long vendorId, StatusAction action, String reason) {
        VendorProfile vendor = find(vendorId);
        vendor.apply(action, trimToNull(reason), clock.instant());
        if (vendor.isOperational()) {
            studyHallRepository.findByVendor_IdAndStatus(vendor.getId(), StudyHallStatus.APPROVED)
                    .forEach(StudyHall::activateIfApproved);
        }
        vendorRepository.saveAndFlush(vendor);
        log.info("Vendor {} -> {} ({})", vendorId, vendor.getStatus(), action);
        return toAdminResponse(vendor);
    }

    private AdminVendorResponse toAdminResponse(VendorProfile vendor) {
        return new AdminVendorResponse(mapper.toResponse(vendor),
                studyHallRepository.countByVendor_Id(vendor.getId()),
                studyHallRepository.countByVendor_IdAndStatus(vendor.getId(), StudyHallStatus.ACTIVE));
    }

    private VendorProfile find(Long vendorId) {
        return vendorRepository.findByVendorId(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));
    }

    private static void apply(VendorProfile profile, VendorProfileRequest request) {
        profile.setBusinessName(request.businessName().trim());
        profile.setContactName(trimToNull(request.contactName()));
        profile.setPhone(trimToNull(request.phone()));
        if (request.email() != null) {
            profile.setEmail(trimToNull(request.email()));
        }
        profile.setAddressLine(trimToNull(request.addressLine()));
        profile.setCity(trimToNull(request.city()));
        profile.setState(trimToNull(request.state()));
        profile.setPincode(trimToNull(request.pincode()));
        profile.setGstNumber(trimToNull(request.gstNumber()));
        profile.setDescription(trimToNull(request.description()));
        profile.setLogoUrl(trimToNull(request.logoUrl()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
