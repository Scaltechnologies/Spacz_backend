package com.spacz.user.dto;

import com.spacz.user.entity.ActivityType;

import java.time.Instant;

public record ActivityResponse(Long id, ActivityType type, String description, String referenceType,
                               Long referenceId, Instant createdAt) {
}
