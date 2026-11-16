package com.spacz.notification.dto;

import com.spacz.notification.entity.Notification;
import com.spacz.notification.entity.NotificationCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "A notification in the caller's inbox")
public record NotificationResponse(
        Long id,
        NotificationCategory category,
        @Schema(description = "Machine-readable event, e.g. BOOKING_CONFIRMED, VENDOR_APPROVED", example = "BOOKING_CONFIRMED")
        String type,
        String title,
        String body,
        String imageUrl,
        @Schema(description = "What the notification is about, for deep links", example = "BOOKING")
        String entityType,
        Long entityId,
        @Schema(description = "Extra deep-link data (string values), also sent in the push payload")
        Map<String, String> data,
        @Schema(description = "true = sent to every account of a role (announcement), false = personal")
        boolean broadcast,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification n, boolean read) {
        return new NotificationResponse(n.getId(), n.getCategory(), n.getType(), n.getTitle(), n.getBody(),
                n.getImageUrl(), n.getEntityType(), n.getEntityId(), n.getData(), n.isBroadcast(), read,
                n.getCreatedAt());
    }
}
