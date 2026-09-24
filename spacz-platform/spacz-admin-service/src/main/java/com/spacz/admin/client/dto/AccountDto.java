package com.spacz.admin.client.dto;

import java.time.Instant;

public record AccountDto(Long id, String email, String phone, String role, String status, boolean passwordSet,
                         Instant lastLoginAt, Instant createdAt) {
}
