package com.spacz.auth.service;

import com.spacz.auth.dto.AccountResponse;
import com.spacz.auth.dto.AccountStatsResponse;
import com.spacz.auth.dto.PageResponse;
import com.spacz.auth.dto.internal.ImportAccountRequest;
import com.spacz.auth.dto.internal.ImportAccountResult;
import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.security.Role;
import org.springframework.data.domain.Pageable;

/**
 * Account administration (admin-service) and legacy import (migration tool) over /internal/accounts.
 */
public interface AccountManagementService {

    PageResponse<AccountResponse> search(String search, Role role, AccountStatus status, Pageable pageable);

    AccountResponse get(Long accountId);

    AccountResponse updateStatus(Long accountId, AccountStatus status);

    AccountStatsResponse stats();

    /** Idempotent on legacyLoginId, then phone, then email. */
    ImportAccountResult importAccount(ImportAccountRequest request);
}
