package com.spacz.notification.dto;

import com.spacz.notification.entity.NotificationCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Only the listed categories change; the others keep their current setting. */
public record UpdatePreferencesRequest(@NotEmpty List<@Valid @NotNull Item> preferences) {

    public record Item(@NotNull NotificationCategory category, @NotNull Boolean pushEnabled) {
    }
}
