package com.spacz.admin.dto;

import com.spacz.admin.entity.PlatformUpdate;

import java.time.Instant;

public record PlatformUpdateResponse(Long id, String title, String description, boolean published,
                                     Instant publishedAt, Instant createdAt, Instant updatedAt) {

    public static PlatformUpdateResponse from(PlatformUpdate update) {
        return new PlatformUpdateResponse(update.getId(), update.getTitle(), update.getDescription(),
                update.isPublished(), update.getPublishedAt(), update.getCreatedAt(), update.getUpdatedAt());
    }
}
