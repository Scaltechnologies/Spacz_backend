package com.spacz.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.client.dto.AdminVendorDto;

/**
 * Vendor business profile (studyhall-service) plus the login account (auth-service).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminVendorDetailResponse(AdminVendorDto vendor, AccountDto account) {
}
