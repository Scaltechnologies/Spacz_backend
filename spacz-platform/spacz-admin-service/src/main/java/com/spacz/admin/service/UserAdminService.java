package com.spacz.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.dto.AdminUserDetailResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

public interface UserAdminService {

    PageResponse<AccountDto> search(String search, String role, String status, Pageable pageable);

    AdminUserDetailResponse get(Long userId);

    JsonNode activity(Long userId, Pageable pageable);

    AccountDto suspend(AuthenticatedUser admin, Long userId, String reason);

    AccountDto activate(AuthenticatedUser admin, Long userId, String reason);
}
