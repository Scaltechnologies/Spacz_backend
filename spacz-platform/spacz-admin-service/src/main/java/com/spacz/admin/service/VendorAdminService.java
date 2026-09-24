package com.spacz.admin.service;

import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.dto.AdminVendorDetailResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

public interface VendorAdminService {

    PageResponse<AdminVendorDto> search(String search, String status, String city, Pageable pageable);

    AdminVendorDetailResponse get(Long vendorId);

    AdminVendorDto approve(AuthenticatedUser admin, Long vendorId, String note);

    AdminVendorDto reject(AuthenticatedUser admin, Long vendorId, String reason);

    AdminVendorDto suspend(AuthenticatedUser admin, Long vendorId, String reason);

    AdminVendorDto activate(AuthenticatedUser admin, Long vendorId, String note);
}
