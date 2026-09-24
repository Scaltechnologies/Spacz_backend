package com.spacz.admin.service.impl;

import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.client.dto.StatusActionDto;
import com.spacz.admin.dto.AdminVendorDetailResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.exception.SpaczException;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import com.spacz.admin.service.VendorAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Each action: studyhall-service enforces the vendor state machine; on success the action is audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorAdminServiceImpl implements VendorAdminService {

    private static final String ENTITY = "VENDOR";

    private final StudyHallServiceClient studyHallClient;
    private final AuthServiceClient authClient;
    private final AuditService auditService;

    @Override
    public PageResponse<AdminVendorDto> search(String search, String status, String city, Pageable pageable) {
        return studyHallClient.searchVendors(search, status, city, pageable);
    }

    @Override
    public AdminVendorDetailResponse get(Long vendorId) {
        AdminVendorDto vendor = studyHallClient.getVendor(vendorId);
        AccountDto account = null;
        try {
            account = authClient.getAccount(vendorId);
        } catch (SpaczException ex) {
            log.warn("Account for vendor {} unavailable: {}", vendorId, ex.getMessage());
        }
        return new AdminVendorDetailResponse(vendor, account);
    }

    @Override
    public AdminVendorDto approve(AuthenticatedUser admin, Long vendorId, String note) {
        return act(admin, vendorId, "APPROVE", note, AuditAction.VENDOR_APPROVED, "Approved vendor");
    }

    @Override
    public AdminVendorDto reject(AuthenticatedUser admin, Long vendorId, String reason) {
        return act(admin, vendorId, "REJECT", reason, AuditAction.VENDOR_REJECTED, "Rejected vendor");
    }

    @Override
    public AdminVendorDto suspend(AuthenticatedUser admin, Long vendorId, String reason) {
        return act(admin, vendorId, "SUSPEND", reason, AuditAction.VENDOR_SUSPENDED, "Suspended vendor");
    }

    @Override
    public AdminVendorDto activate(AuthenticatedUser admin, Long vendorId, String note) {
        return act(admin, vendorId, "ACTIVATE", note, AuditAction.VENDOR_ACTIVATED, "Activated vendor");
    }

    private AdminVendorDto act(AuthenticatedUser admin, Long vendorId, String action, String reason,
                               AuditAction auditAction, String verb) {
        AdminVendorDto result = studyHallClient.changeVendorStatus(vendorId, new StatusActionDto(action, reason));
        auditService.record(admin, auditAction, ENTITY, vendorId,
                verb + " " + result.profile().businessName() + (reason != null && !reason.isBlank() ? ": " + reason : ""));
        return result;
    }
}
