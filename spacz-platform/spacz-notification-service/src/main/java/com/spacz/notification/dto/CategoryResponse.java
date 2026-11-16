package com.spacz.notification.dto;

import com.spacz.notification.entity.NotificationCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryResponse(NotificationCategory category, String label,
                               @Schema(description = "Android notification channel the push uses for this category",
                                       example = "spacz_booking")
                               String androidChannelId) {

    public static CategoryResponse from(NotificationCategory category) {
        return new CategoryResponse(category, category.getLabel(), category.androidChannelId());
    }
}
