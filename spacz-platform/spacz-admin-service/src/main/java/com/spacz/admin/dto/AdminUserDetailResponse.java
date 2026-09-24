package com.spacz.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.client.dto.UserProfileDto;

import java.util.List;

/**
 * An account plus what the owning services know about it: for students the profile, exam choices
 * and hall memberships; for vendors the vendor profile. Parts that could not be loaded are omitted
 * and listed in {@code warnings}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminUserDetailResponse(AccountDto account, UserProfileDto profile, JsonNode programs,
                                      JsonNode enrollments, AdminVendorDto vendor, List<String> warnings) {
}
