package com.spacz.notification.dto;

import com.spacz.notification.entity.NotificationCategory;

public record PreferenceResponse(NotificationCategory category, String label, boolean pushEnabled) {
}
