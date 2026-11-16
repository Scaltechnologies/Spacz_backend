package com.spacz.notification.dto.admin;

import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.Notification;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.entity.PushStatus;

import java.time.Instant;
import java.util.Map;

public record AdminNotificationResponse(Long id, Long recipientUserId, Audience audience, NotificationCategory category,
                                        String type, String title, String body, String imageUrl,
                                        Map<String, String> data, String sourceService, Long createdBy,
                                        PushStatus pushStatus, int pushSuccess, int pushFailure, Instant pushedAt,
                                        Instant createdAt) {

    public static AdminNotificationResponse from(Notification n) {
        return new AdminNotificationResponse(n.getId(), n.getRecipientUserId(), n.getAudience(), n.getCategory(),
                n.getType(), n.getTitle(), n.getBody(), n.getImageUrl(), n.getData(), n.getSourceService(),
                n.getCreatedBy(), n.getPushStatus(), n.getPushSuccess(), n.getPushFailure(), n.getPushedAt(),
                n.getCreatedAt());
    }
}
