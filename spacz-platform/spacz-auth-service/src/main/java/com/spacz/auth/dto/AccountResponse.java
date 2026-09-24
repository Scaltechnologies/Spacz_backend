package com.spacz.auth.dto;

import com.spacz.auth.entity.AccountStatus;
import com.spacz.auth.security.Role;

import java.time.Instant;

public record AccountResponse(Long id, String email, String phone, Role role, AccountStatus status,
                              boolean passwordSet, Instant lastLoginAt, Instant createdAt) {
}
