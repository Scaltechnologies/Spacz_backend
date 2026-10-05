package com.spacz.admin.dto;

import com.spacz.admin.entity.Offer;
import com.spacz.admin.entity.PlatformUpdate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * A student-facing notification. OFFER items are the live offers; UPDATE items are published
 * platform updates. {@code id} is unique per {@code type}.
 */
public record NotificationResponse(Long id, Type type, String title, String description,
                                   @Schema(description = "OFFER only") Instant startAt,
                                   @Schema(description = "OFFER only") Instant endAt,
                                   Instant createdAt) {

    public enum Type { OFFER, UPDATE }

    public static NotificationResponse from(Offer offer) {
        return new NotificationResponse(offer.getId(), Type.OFFER, offer.getTitle(), offer.getDescription(),
                offer.getStartAt(), offer.getEndAt(), offer.getCreatedAt());
    }

    public static NotificationResponse from(PlatformUpdate update) {
        return new NotificationResponse(update.getId(), Type.UPDATE, update.getTitle(), update.getDescription(),
                null, null, update.getPublishedAt() != null ? update.getPublishedAt() : update.getCreatedAt());
    }
}
