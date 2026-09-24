package com.spacz.admin.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.UserServiceClient;
import com.spacz.admin.client.dto.AccountDto;
import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.client.dto.UserProfileDto;
import com.spacz.admin.dto.AdminUserDetailResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.exception.BusinessRuleException;
import com.spacz.admin.exception.SpaczException;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import com.spacz.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final AuthServiceClient authClient;
    private final UserServiceClient userClient;
    private final StudyHallServiceClient studyHallClient;
    private final AuditService auditService;

    @Override
    public PageResponse<AccountDto> search(String search, String role, String status, Pageable pageable) {
        return authClient.searchAccounts(search, role, status, pageable);
    }

    /** Composes the account (auth) with what user- and studyhall-service know; each part is best effort. */
    @Override
    public AdminUserDetailResponse get(Long userId) {
        AccountDto account = authClient.getAccount(userId);
        List<String> warnings = new ArrayList<>();
        UserProfileDto profile = null;
        JsonNode programs = null;
        JsonNode enrollments = null;
        AdminVendorDto vendor = null;
        if ("USER".equals(account.role())) {
            profile = attempt(() -> userClient.getProfile(userId), "profile", warnings);
            programs = attempt(() -> userClient.programs(userId), "programs", warnings);
            enrollments = attempt(() -> studyHallClient.enrollments(userId), "enrollments", warnings);
        } else if ("VENDOR".equals(account.role())) {
            vendor = attempt(() -> studyHallClient.getVendor(userId), "vendor profile", warnings);
        }
        return new AdminUserDetailResponse(account, profile, programs, enrollments, vendor,
                warnings.isEmpty() ? null : warnings);
    }

    @Override
    public JsonNode activity(Long userId, Pageable pageable) {
        return userClient.activity(userId, pageable);
    }

    @Override
    public AccountDto suspend(AuthenticatedUser admin, Long userId, String reason) {
        preventSelfAction(admin, userId);
        AccountDto account = authClient.updateStatus(userId, "SUSPENDED");
        auditService.record(admin, AuditAction.USER_SUSPENDED, "USER_ACCOUNT", userId,
                "Suspended " + account.role() + " account " + identifier(account) + ": " + reason);
        return account;
    }

    @Override
    public AccountDto activate(AuthenticatedUser admin, Long userId, String reason) {
        preventSelfAction(admin, userId);
        AccountDto account = authClient.updateStatus(userId, "ACTIVE");
        auditService.record(admin, AuditAction.USER_ACTIVATED, "USER_ACCOUNT", userId,
                "Activated " + account.role() + " account " + identifier(account) + (reason != null ? ": " + reason : ""));
        return account;
    }

    private static <T> T attempt(Supplier<T> call, String part, List<String> warnings) {
        try {
            return call.get();
        } catch (SpaczException ex) {
            warnings.add(part + " unavailable: " + ex.getMessage());
            return null;
        }
    }

    private static String identifier(AccountDto account) {
        return account.email() != null ? account.email() : account.phone();
    }

    private static void preventSelfAction(AuthenticatedUser admin, Long userId) {
        if (admin.userId().equals(userId)) {
            throw new BusinessRuleException("Administrators cannot change the status of their own account");
        }
    }
}
