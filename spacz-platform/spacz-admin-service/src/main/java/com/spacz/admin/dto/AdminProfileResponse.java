package com.spacz.admin.dto;

import java.time.Instant;

public record AdminProfileResponse(Long id, Long adminUserId, String fullName, String email, String phone, String title,
                                   Instant lastSeenAt, Instant createdAt, Instant updatedAt) {
}
